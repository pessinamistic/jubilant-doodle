package com.dbdeployer.api;

import com.dbdeployer.api.dto.DeploymentActivityResponse;
import com.dbdeployer.api.dto.MetricsHistoryResponse;
import com.dbdeployer.config.DockerHealthChecker;
import com.dbdeployer.config.DockerHealthChecker.DockerStatus;
import com.dbdeployer.service.MetricsHistoryService;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Metrics endpoints for the Port Wrangler system dashboard.
 *
 * <ul>
 *   <li>{@code GET /api/system/metrics/history} — Rolling 30-min JVM + pool time-series
 *   <li>{@code GET /api/system/metrics/activity} — Deployment frequency + instance breakdown
 *   <li>{@code GET /api/system/docker-status} — Live Docker daemon reachability probe
 * </ul>
 */
@RestController
@RequestMapping("/api/system/metrics")
public class SystemMetricsController {

  private final MetricsHistoryService historyService;
  private final JdbcTemplate jdbc;
  private final DockerHealthChecker dockerHealthChecker;

  public SystemMetricsController(
      MetricsHistoryService historyService,
      JdbcTemplate jdbc,
      DockerHealthChecker dockerHealthChecker) {
    this.historyService = historyService;
    this.jdbc = jdbc;
    this.dockerHealthChecker = dockerHealthChecker;
  }

  /** Last N JVM + HikariCP pool samples (one per ~30 s, up to 30-min window). */
  @GetMapping("/history")
  public MetricsHistoryResponse history() {
    return historyService.getHistory();
  }

  /**
   * Deployment frequency and instance breakdown suitable for chart rendering:
   *
   * <ul>
   *   <li>Deployments per day for the last 30 days
   *   <li>Instance counts grouped by database type
   *   <li>Instance counts grouped by runtime status
   * </ul>
   */
  @GetMapping("/activity")
  public DeploymentActivityResponse activity() {

    // ── Deployments per calendar day (last 30 days) ─────────────────────
    List<DeploymentActivityResponse.DayCount> byDay =
        jdbc.query(
            """
                SELECT created_at::date AS deploy_date, COUNT(*) AS cnt
                FROM deployment_config
                WHERE is_system = false
                  AND created_at >= NOW() - INTERVAL '30 days'
                GROUP BY created_at::date
                ORDER BY deploy_date
                """,
            (rs, i) ->
                new DeploymentActivityResponse.DayCount(
                    rs.getDate("deploy_date").toLocalDate().toString(), rs.getLong("cnt")));

    // ── Instances by database type ───────────────────────────────────────
    List<DeploymentActivityResponse.LabelCount> byType =
        jdbc.query(
            """
                SELECT db_type AS label, COUNT(*) AS cnt
                FROM deployment_config
                WHERE is_system = false
                GROUP BY db_type
                ORDER BY cnt DESC
                """,
            (rs, i) ->
                new DeploymentActivityResponse.LabelCount(
                    rs.getString("label"), rs.getLong("cnt")));

    // ── Instances by status ──────────────────────────────────────────────
    List<DeploymentActivityResponse.LabelCount> byStatus =
        jdbc.query(
            """
                SELECT dc.status AS label, COUNT(*) AS cnt
                FROM deployed_container dc
                JOIN deployment_config cfg ON cfg.id = dc.config_id
                WHERE cfg.is_system = false
                GROUP BY dc.status
                ORDER BY cnt DESC
                """,
            (rs, i) ->
                new DeploymentActivityResponse.LabelCount(
                    rs.getString("label"), rs.getLong("cnt")));

    return new DeploymentActivityResponse(byDay, byType, byStatus);
  }

  /**
   * Live probe of the Docker daemon — useful for the UI to surface a warning banner if Docker goes
   * down after the app has started.
   */
  @GetMapping("/docker-status")
  public DockerStatus dockerStatus() {
    return dockerHealthChecker.check();
  }
}

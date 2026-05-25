package com.dbdeployer.api;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbdeployer.api.dto.DeploymentActivityResponse;
import com.dbdeployer.api.dto.MetricsHistoryResponse;
import com.dbdeployer.service.MetricsHistoryService;

/**
 * Metrics endpoints for the Port Wrangler system dashboard.
 *
 * <ul>
 *   <li>{@code GET /api/system/metrics/history}  — Rolling 30-min JVM + pool time-series</li>
 *   <li>{@code GET /api/system/metrics/activity} — Deployment frequency + instance breakdown</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/system/metrics")
public class SystemMetricsController {

    private final MetricsHistoryService historyService;
    private final JdbcTemplate          jdbc;

    public SystemMetricsController(MetricsHistoryService historyService, JdbcTemplate jdbc) {
        this.historyService = historyService;
        this.jdbc           = jdbc;
    }

    /** Last N JVM + HikariCP pool samples (one per ~30 s, up to 30-min window). */
    @GetMapping("/history")
    public MetricsHistoryResponse history() {
        return historyService.getHistory();
    }

    /**
     * Deployment frequency and instance breakdown suitable for chart rendering:
     * <ul>
     *   <li>Deployments per day for the last 30 days</li>
     *   <li>Instance counts grouped by database type</li>
     *   <li>Instance counts grouped by runtime status</li>
     * </ul>
     */
    @GetMapping("/activity")
    public DeploymentActivityResponse activity() {

        // ── Deployments per calendar day (last 30 days) ─────────────────────
        List<DeploymentActivityResponse.DayCount> byDay = jdbc.query("""
                SELECT CAST(created_at AS DATE) AS deploy_date, COUNT(*) AS cnt
                FROM deployment_config
                WHERE is_system = false
                  AND created_at >= DATEADD('DAY', -30, CURRENT_TIMESTAMP)
                GROUP BY CAST(created_at AS DATE)
                ORDER BY deploy_date
                """,
                (rs, i) -> new DeploymentActivityResponse.DayCount(
                        rs.getDate("deploy_date").toLocalDate().toString(),
                        rs.getLong("cnt")
                ));

        // ── Instances by database type ───────────────────────────────────────
        List<DeploymentActivityResponse.LabelCount> byType = jdbc.query("""
                SELECT db_type AS label, COUNT(*) AS cnt
                FROM deployment_config
                WHERE is_system = false
                GROUP BY db_type
                ORDER BY cnt DESC
                """,
                (rs, i) -> new DeploymentActivityResponse.LabelCount(
                        rs.getString("label"), rs.getLong("cnt")
                ));

        // ── Instances by status ──────────────────────────────────────────────
        List<DeploymentActivityResponse.LabelCount> byStatus = jdbc.query("""
                SELECT dc.status AS label, COUNT(*) AS cnt
                FROM deployed_container dc
                JOIN deployment_config cfg ON cfg.id = dc.config_id
                WHERE cfg.is_system = false
                GROUP BY dc.status
                ORDER BY cnt DESC
                """,
                (rs, i) -> new DeploymentActivityResponse.LabelCount(
                        rs.getString("label"), rs.getLong("cnt")
                ));

        return new DeploymentActivityResponse(byDay, byType, byStatus);
    }
}

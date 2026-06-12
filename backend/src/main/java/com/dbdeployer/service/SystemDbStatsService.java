package com.dbdeployer.service;

import com.dbdeployer.api.dto.SystemDbStatsResponse;
import com.dbdeployer.api.dto.SystemDbStatsResponse.AppInfo;
import com.dbdeployer.api.dto.SystemDbStatsResponse.DbInfo;
import com.dbdeployer.api.dto.SystemDbStatsResponse.JvmInfo;
import com.dbdeployer.api.dto.SystemDbStatsResponse.PoolInfo;
import com.dbdeployer.api.dto.SystemDbStatsResponse.SchemaInfo;
import com.dbdeployer.api.dto.SystemDbStatsResponse.TableStat;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Produces a live snapshot of Port Wrangler's system (PostgreSQL) database stats. */
@Slf4j
@Service
public class SystemDbStatsService {

  private static final List<String> TRACKED_TABLES =
      List.of("deployment_config", "deployed_container", "deployment_pipeline", "pipeline_step");

  private final DataSource dataSource;
  private final JdbcTemplate jdbc;
  private final Environment env;

  public SystemDbStatsService(DataSource dataSource, JdbcTemplate jdbc, Environment env) {
    this.dataSource = dataSource;
    this.jdbc = jdbc;
    this.env = env;
  }

  public SystemDbStatsResponse getStats() {
    return new SystemDbStatsResponse(
        buildDbInfo(), buildSchemaInfo(), buildPoolInfo(), buildAppInfo(), buildJvmInfo());
  }

  // ── Sections ──────────────────────────────────────────────────────────────

  private DbInfo buildDbInfo() {
    String banner = safeQuery("SELECT version()", "unknown");
    String version = parsePostgresVersion(banner);
    long dbSize = safeCountLong("SELECT pg_database_size(current_database())");

    String url = env.getProperty("spring.datasource.url", "");
    String host = extractPgHost(url);
    int port = extractPgPort(url);

    return new DbInfo("PostgreSQL", version, host, port, "dbdeployer", dbSize);
  }

  private SchemaInfo buildSchemaInfo() {
    List<TableStat> tables =
        TRACKED_TABLES.stream()
            .map(
                table -> {
                  Long count = safeCount(table);
                  return new TableStat(table, count != null ? count : -1L);
                })
            .toList();
    return new SchemaInfo(tables.size(), tables);
  }

  private PoolInfo buildPoolInfo() {
    if (dataSource instanceof HikariDataSource hds) {
      var pool = hds.getHikariPoolMXBean();
      if (pool != null) {
        return new PoolInfo(
            hds.getMaximumPoolSize(),
            pool.getActiveConnections(),
            pool.getIdleConnections(),
            pool.getThreadsAwaitingConnection(),
            pool.getTotalConnections());
      }
    }
    return new PoolInfo(0, 0, 0, 0, 0);
  }

  private AppInfo buildAppInfo() {
    var mx = ManagementFactory.getRuntimeMXBean();
    long uptimeMs = mx.getUptime();
    long startMs = mx.getStartTime();
    String started =
        Instant.ofEpochMilli(startMs)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    return new AppInfo(uptimeMs / 1000L, started);
  }

  private JvmInfo buildJvmInfo() {
    Runtime rt = Runtime.getRuntime();
    long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    long max = rt.maxMemory() / (1024 * 1024);
    return new JvmInfo(used, max);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Extracts the host from a Postgres JDBC URL like {@code
   * jdbc:postgresql://localhost:5499/dbdeployer}.
   */
  static String extractPgHost(String url) {
    if (url == null || url.isBlank()) return "localhost";
    try {
      // Strip jdbc: prefix so java.net.URI can parse it
      java.net.URI uri = new java.net.URI(url.replaceFirst("^jdbc:", ""));
      String host = uri.getHost();
      return host != null ? host : "localhost";
    } catch (Exception e) {
      return "localhost";
    }
  }

  /**
   * Extracts the port from a Postgres JDBC URL. Falls back to {@code 5432} if the URL contains no
   * explicit port.
   */
  static int extractPgPort(String url) {
    if (url == null || url.isBlank()) return 5432;
    try {
      java.net.URI uri = new java.net.URI(url.replaceFirst("^jdbc:", ""));
      int p = uri.getPort();
      return p > 0 ? p : 5432;
    } catch (Exception e) {
      return 5432;
    }
  }

  /**
   * Parses a short version string from the Postgres {@code version()} banner. e.g. {@code
   * "PostgreSQL 16.3 on aarch64..."} → {@code "16.3"}
   */
  private static String parsePostgresVersion(String banner) {
    if (banner == null) return "unknown";
    String[] parts = banner.split("\\s+");
    return parts.length >= 2 ? parts[1] : banner;
  }

  private String safeQuery(String sql, String fallback) {
    try {
      return jdbc.queryForObject(sql, String.class);
    } catch (Exception e) {
      log.debug("safeQuery failed [{}]: {}", sql, e.getMessage());
      return fallback;
    }
  }

  private long safeCountLong(String sql) {
    try {
      Long v = jdbc.queryForObject(sql, Long.class);
      return v != null ? v : 0L;
    } catch (Exception e) {
      log.debug("safeCountLong failed [{}]: {}", sql, e.getMessage());
      return 0L;
    }
  }

  private Long safeCount(String table) {
    try {
      return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    } catch (Exception e) {
      log.debug("safeCount failed [{}]: {}", table, e.getMessage());
      return null;
    }
  }
}

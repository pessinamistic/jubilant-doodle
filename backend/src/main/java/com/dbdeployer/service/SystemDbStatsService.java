package com.dbdeployer.service;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.dbdeployer.api.dto.SystemDbStatsResponse;
import com.dbdeployer.api.dto.SystemDbStatsResponse.AppInfo;
import com.dbdeployer.api.dto.SystemDbStatsResponse.DbInfo;
import com.dbdeployer.api.dto.SystemDbStatsResponse.JvmInfo;
import com.dbdeployer.api.dto.SystemDbStatsResponse.PoolInfo;
import com.dbdeployer.api.dto.SystemDbStatsResponse.SchemaInfo;
import com.dbdeployer.api.dto.SystemDbStatsResponse.TableStat;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Produces a live snapshot of Port Wrangler's embedded H2 system database stats.
 */
@Service
public class SystemDbStatsService {

    private static final Logger log = LoggerFactory.getLogger(SystemDbStatsService.class);

    private static final List<String> TRACKED_TABLES = List.of(
            "deployment_config",
            "deployed_container",
            "deployment_pipeline",
            "pipeline_step"
    );

    private final DataSource   dataSource;
    private final JdbcTemplate jdbc;
    private final Environment  env;

    public SystemDbStatsService(DataSource dataSource, JdbcTemplate jdbc, Environment env) {
        this.dataSource = dataSource;
        this.jdbc       = jdbc;
        this.env        = env;
    }

    public SystemDbStatsResponse getStats() {
        return new SystemDbStatsResponse(
                buildDbInfo(),
                buildSchemaInfo(),
                buildPoolInfo(),
                buildAppInfo(),
                buildJvmInfo()
        );
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private DbInfo buildDbInfo() {
        String version = safeQuery("SELECT H2VERSION()", "unknown");
        String url     = env.getProperty("spring.datasource.url", "");

        // Parse the file path out of jdbc:h2:file:./data/dbdeployer;...
        String filePath = extractH2FilePath(url);
        long   fileSize = resolveFileSize(filePath);

        return new DbInfo("H2 (embedded)", version, filePath, fileSize);
    }

    private SchemaInfo buildSchemaInfo() {
        List<TableStat> tables = TRACKED_TABLES.stream()
                .map(table -> {
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
                        pool.getTotalConnections()
                );
            }
        }
        return new PoolInfo(0, 0, 0, 0, 0);
    }

    private AppInfo buildAppInfo() {
        var mx = ManagementFactory.getRuntimeMXBean();
        long uptimeMs  = mx.getUptime();
        long startMs   = mx.getStartTime();
        String started = Instant.ofEpochMilli(startMs)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new AppInfo(uptimeMs / 1000L, started);
    }

    private JvmInfo buildJvmInfo() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max  = rt.maxMemory() / (1024 * 1024);
        return new JvmInfo(used, max);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts the file path from a H2 JDBC URL like
     * {@code jdbc:h2:file:./data/dbdeployer;AUTO_SERVER=TRUE;...}
     * Returns the path without the H2 {@code .mv.db} extension.
     */
    static String extractH2FilePath(String url) {
        if (url == null) return "";
        // Strip jdbc:h2:file: prefix
        String trimmed = url.replaceFirst("(?i)^jdbc:h2:file:", "");
        // Strip any ;key=value options
        int semi = trimmed.indexOf(';');
        return semi >= 0 ? trimmed.substring(0, semi) : trimmed;
    }

    /** Returns the size in bytes of the H2 .mv.db file, or 0 if not found. */
    private long resolveFileSize(String base) {
        if (base == null || base.isBlank()) return 0L;
        try {
            Path p = Path.of(base + ".mv.db");
            return Files.exists(p) ? Files.size(p) : 0L;
        } catch (Exception e) {
            log.debug("Could not read H2 file size: {}", e.getMessage());
            return 0L;
        }
    }

    private String safeQuery(String sql, String fallback) {
        try {
            return jdbc.queryForObject(sql, String.class);
        } catch (Exception e) {
            return fallback;
        }
    }

    private Long safeCount(String table) {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        } catch (Exception e) {
            return null;
        }
    }
}

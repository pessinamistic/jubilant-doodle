package com.dbdeployer.config;

import com.dbdeployer.model.*;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One-time (idempotent) migration that copies rows from the legacy
 * {@code db_instances} table into the new {@code deployment_config} /
 * {@code deployed_container} tables.
 *
 * Uses raw JDBC so it has no dependency on the deleted {@code DbInstance} JPA entity.
 * Safe to run on every boot — rows already present in {@code deployment_config} are skipped.
 * Runs before {@link SystemDbRegistrar} (Order 1 vs 2).
 */
@Component
@Order(1)
public class LegacyDataMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyDataMigrator.class);

    private final JdbcTemplate                jdbc;
    private final DeploymentConfigRepository  configRepo;
    private final DeployedContainerRepository containerRepo;

    public LegacyDataMigrator(JdbcTemplate jdbc,
                               DeploymentConfigRepository configRepo,
                               DeployedContainerRepository containerRepo) {
        this.jdbc          = jdbc;
        this.configRepo    = configRepo;
        this.containerRepo = containerRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // If the legacy table doesn't exist yet, do nothing
        if (!tableExists("db_instances")) {
            log.debug("LegacyDataMigrator: db_instances table not found — skipping");
            return;
        }

        var rows = jdbc.query(
                "SELECT * FROM db_instances",
                (rs, n) -> readRow(rs));

        if (rows.isEmpty()) {
            log.debug("LegacyDataMigrator: db_instances is empty — nothing to migrate");
            return;
        }

        int migrated = 0, skipped = 0;

        for (var row : rows) {
            // System row is managed by SystemDbRegistrar with fixed id "system"
            if (row.isSystem || "system".equals(row.id)) { skipped++; continue; }

            if (configRepo.existsById(row.id)) {
                log.debug("LegacyDataMigrator: skipping '{}' — already migrated", row.name);
                skipped++;
                continue;
            }

            try {
                migrateOne(row);
                migrated++;
                log.info("LegacyDataMigrator: migrated '{}' ({})", row.name, row.id);
            } catch (Exception e) {
                log.error("LegacyDataMigrator: failed on '{}' ({}): {}",
                        row.name, row.id, e.getMessage(), e);
            }
        }

        log.info("LegacyDataMigrator: done — {} migrated, {} skipped", migrated, skipped);
    }

    // ── private ────────────────────────────────────────────────────────────────

    private void migrateOne(LegacyRow r) {
        // ── DeploymentConfig ──
        DeploymentConfig config = new DeploymentConfig();
        config.setId(r.id);
        config.setName(r.name);
        config.setDbType(r.dbType);
        config.setVersion(r.version != null ? r.version : "unknown");
        config.setHostPort(r.hostPort);
        config.setContainerPort(r.containerPort);
        config.setUsername(r.username);
        config.setPassword(r.password);
        config.setDatabaseName(r.databaseName);
        config.setDeployMethod(r.deployMethod != null ? r.deployMethod : DeployMethod.DOCKER);
        config.setExtraEnvJson(r.extraEnvJson);
        config.setSystem(r.isSystem);
        config.setImported(r.isImported);
        // Preserve original timestamps (null-safe @PrePersist won't overwrite these)
        config.setCreatedAt(r.createdAt != null ? r.createdAt : LocalDateTime.now());
        config.setUpdatedAt(r.updatedAt != null ? r.updatedAt : LocalDateTime.now());
        configRepo.save(config);

        // ── DeployedContainer ──
        DeployedContainer container = new DeployedContainer();
        container.setId(UUID.randomUUID().toString());
        container.setConfig(config);
        container.setContainerId(r.containerId);
        container.setContainerName(r.containerName);
        // REMOVING mid-crash → treat as REMOVED
        InstanceStatus status = r.status != null ? r.status : InstanceStatus.STOPPED;
        if (status == InstanceStatus.REMOVING) status = InstanceStatus.REMOVED;
        container.setStatus(status);
        container.setDataDirectory(r.dataDirectory);
        container.setStartedAt(r.startedAt);
        container.setCreatedAt(r.createdAt != null ? r.createdAt : LocalDateTime.now());
        container.setUpdatedAt(r.updatedAt != null ? r.updatedAt : LocalDateTime.now());
        containerRepo.save(container);
    }

    private boolean tableExists(String table) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_name = ?", Integer.class, table);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static LegacyRow readRow(ResultSet rs) throws SQLException {
        var r = new LegacyRow();
        r.id            = rs.getString("id");
        r.name          = rs.getString("name");
        r.dbType        = parseEnum(DbType.class,        rs.getString("db_type"));
        r.version       = rs.getString("version");
        r.hostPort      = rs.getInt("host_port");
        r.containerPort = rs.getInt("container_port");
        r.username      = rs.getString("username");
        r.password      = rs.getString("password");
        r.databaseName  = rs.getString("database_name");
        r.containerId   = rs.getString("container_id");
        r.containerName = rs.getString("container_name");
        r.status        = parseEnum(InstanceStatus.class, rs.getString("status"));
        r.deployMethod  = parseEnum(DeployMethod.class,   rs.getString("deploy_method"));
        r.dataDirectory = rs.getString("data_directory");
        r.extraEnvJson  = rs.getString("extra_env");
        r.isSystem      = rs.getBoolean("is_system");
        r.isImported    = rs.getBoolean("is_imported");
        r.createdAt     = rs.getObject("created_at",  LocalDateTime.class);
        r.updatedAt     = rs.getObject("updated_at",  LocalDateTime.class);
        r.startedAt     = rs.getObject("started_at",  LocalDateTime.class);
        return r;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException e) { return null; }
    }

    /** Lightweight struct — only used inside this class. */
    private static class LegacyRow {
        String         id, name, version, username, password, databaseName;
        String         containerId, containerName, dataDirectory, extraEnvJson;
        DbType         dbType;
        InstanceStatus status;
        DeployMethod   deployMethod;
        int            hostPort, containerPort;
        boolean        isSystem, isImported;
        LocalDateTime  createdAt, updatedAt, startedAt;
    }
}

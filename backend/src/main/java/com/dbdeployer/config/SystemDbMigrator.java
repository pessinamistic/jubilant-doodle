package com.dbdeployer.config;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.pipeline.model.DeployErrorCode;
import com.dbdeployer.pipeline.model.DeploymentPipeline;
import com.dbdeployer.pipeline.model.PipelineStatus;
import com.dbdeployer.pipeline.model.PipelineStep;
import com.dbdeployer.pipeline.model.StepStatus;
import com.dbdeployer.pipeline.model.StepType;
import com.dbdeployer.pipeline.store.DeploymentPipelineRepository;
import com.dbdeployer.pipeline.store.PipelineStepRepository;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;

/**
 * One-shot migration from the legacy PostgreSQL system container to the new embedded H2 store.
 *
 * Runs at {@code @Order(1)}, before {@link SystemDbRegistrar} ({@code @Order(2)}).
 *
 * Logic:
 * <ol>
 *   <li>Skip if the marker file {@code data/.migrated-from-postgres} already exists.</li>
 *   <li>Skip if H2 already has data (fresh install or already migrated).</li>
 *   <li>Skip if the old Postgres is not reachable at {@code localhost:5499}.</li>
 *   <li>Otherwise: copy all 4 tables from old Postgres into H2 via raw JDBC,
 *       then write the marker so this never runs again.</li>
 * </ol>
 *
 * The PostgreSQL JDBC driver is on the runtime classpath as {@code runtimeOnly}
 * solely to support this migration path.  Once the marker exists the driver is
 * loaded but never used.
 */
@Component
@Order(1)
public class SystemDbMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemDbMigrator.class);

    private static final String LEGACY_JDBC_URL  = "jdbc:postgresql://localhost:5499/dbdeployer";
    private static final String LEGACY_USER       = "dbdeployer";
    private static final String LEGACY_PASSWORD   = "dbdeployer_internal";
    static final         Path   MARKER            = Path.of("data", ".migrated-from-postgres");

    private final DeploymentConfigRepository   configRepo;
    private final DeployedContainerRepository  containerRepo;
    private final DeploymentPipelineRepository pipelineRepo;
    private final PipelineStepRepository       stepRepo;

    public SystemDbMigrator(DeploymentConfigRepository configRepo,
                            DeployedContainerRepository containerRepo,
                            DeploymentPipelineRepository pipelineRepo,
                            PipelineStepRepository stepRepo) {
        this.configRepo    = configRepo;
        this.containerRepo = containerRepo;
        this.pipelineRepo  = pipelineRepo;
        this.stepRepo      = stepRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // ── 1. Already migrated? ─────────────────────────────────────────────
        if (Files.exists(MARKER)) {
            log.debug("SystemDbMigrator: marker found, skipping legacy migration");
            return;
        }

        // ── 2. H2 already has data? (parallel install / prior run) ───────────
        if (configRepo.count() > 0) {
            log.info("SystemDbMigrator: H2 already contains data — writing marker, skipping migration");
            writeMarker("already-has-data");
            return;
        }

        // ── 3. Old Postgres reachable? ───────────────────────────────────────
        if (!isPortOpen("localhost", 5499)) {
            log.info("SystemDbMigrator: no legacy Postgres found at port 5499 — fresh install");
            writeMarker("fresh-install");
            return;
        }

        // ── 4. Migrate ───────────────────────────────────────────────────────
        log.info("SystemDbMigrator: legacy Postgres detected at port 5499 — starting migration to H2...");
        try {
            int configs     = migrateConfigs();
            int containers  = migrateContainers();
            int pipelines   = migratePipelines();
            int steps       = migrateSteps();

            writeMarker("migrated");
            log.info("SystemDbMigrator: migration complete — {} configs, {} containers, {} pipelines, {} steps",
                    configs, containers, pipelines, steps);
            log.info("SystemDbMigrator: the old container 'dbdeployer-system-db' is no longer needed. " +
                    "You can remove it with: docker rm -f dbdeployer-system-db");
        } catch (Exception e) {
            log.warn("SystemDbMigrator: migration failed ({}). " +
                    "Starting with an empty database. " +
                    "Use the Discover feature to re-import your existing containers.", e.getMessage());
            log.debug("SystemDbMigrator: migration error detail", e);
            writeMarker("migration-failed");
        }
    }

    // ── Table migrations ───────────────────────────────────────────────────────

    private int migrateConfigs() throws SQLException {
        int count = 0;
        try (Connection pg = legacyConnection();
             Statement  st = pg.createStatement();
             ResultSet  rs = st.executeQuery(
                     "SELECT id, name, db_type, version, host_port, container_port, " +
                     "username, password, database_name, extra_env_json, deploy_method, " +
                     "is_system, is_imported, created_at, updated_at " +
                     "FROM deployment_config")) {

            while (rs.next()) {
                String id = rs.getString("id");
                if (SystemDbRegistrar.SYSTEM_CONFIG_ID.equals(id)) continue; // re-created by SystemDbRegistrar

                DeploymentConfig c = new DeploymentConfig();
                c.setId(id);
                c.setName(rs.getString("name"));
                c.setDbType(DbType.valueOf(rs.getString("db_type")));
                c.setVersion(rs.getString("version"));
                c.setHostPort(rs.getInt("host_port"));
                c.setContainerPort(rs.getInt("container_port"));
                c.setUsername(rs.getString("username"));
                c.setPassword(rs.getString("password"));
                c.setDatabaseName(rs.getString("database_name"));
                c.setExtraEnvJson(rs.getString("extra_env_json"));
                c.setDeployMethod(DeployMethod.valueOf(rs.getString("deploy_method")));
                c.setSystem(rs.getBoolean("is_system"));
                c.setImported(rs.getBoolean("is_imported"));
                setCreatedAt(c, rs.getTimestamp("created_at"));
                setUpdatedAt(c, rs.getTimestamp("updated_at"));
                configRepo.save(c);
                count++;
            }
        }
        return count;
    }

    private int migrateContainers() throws SQLException {
        int count = 0;
        try (Connection pg = legacyConnection();
             Statement  st = pg.createStatement();
             ResultSet  rs = st.executeQuery(
                     "SELECT id, config_id, container_id, container_name, status, " +
                     "data_directory, started_at, removed_at, latest_pipeline_id, " +
                     "created_at, updated_at " +
                     "FROM deployed_container")) {

            while (rs.next()) {
                String configId = rs.getString("config_id");
                if (SystemDbRegistrar.SYSTEM_CONFIG_ID.equals(configId)) continue;

                DeploymentConfig config = configRepo.findById(configId).orElse(null);
                if (config == null) continue; // orphaned row — skip

                DeployedContainer dc = new DeployedContainer();
                dc.setId(rs.getString("id"));
                dc.setConfig(config);
                dc.setContainerId(rs.getString("container_id"));
                dc.setContainerName(rs.getString("container_name"));
                dc.setStatus(InstanceStatus.valueOf(rs.getString("status")));
                dc.setDataDirectory(rs.getString("data_directory"));
                dc.setStartedAt(toLocalDateTime(rs.getTimestamp("started_at")));
                dc.setRemovedAt(toLocalDateTime(rs.getTimestamp("removed_at")));
                dc.setLatestPipelineId(rs.getString("latest_pipeline_id"));
                setContainerCreatedAt(dc, rs.getTimestamp("created_at"));
                setContainerUpdatedAt(dc, rs.getTimestamp("updated_at"));
                containerRepo.save(dc);
                count++;
            }
        }
        return count;
    }

    private int migratePipelines() throws SQLException {
        int count = 0;
        try (Connection pg = legacyConnection();
             Statement  st = pg.createStatement();
             ResultSet  rs = st.executeQuery(
                     "SELECT id, config_id, status, error_code, error_message, " +
                     "created_at, started_at, completed_at " +
                     "FROM deployment_pipeline")) {

            while (rs.next()) {
                DeploymentPipeline p = new DeploymentPipeline();
                p.setId(rs.getString("id"));
                p.setConfigId(rs.getString("config_id"));
                p.setStatus(PipelineStatus.valueOf(rs.getString("status")));
                String ec = rs.getString("error_code");
                if (ec != null) p.setErrorCode(DeployErrorCode.valueOf(ec));
                p.setErrorMessage(rs.getString("error_message"));
                setPipelineCreatedAt(p, rs.getTimestamp("created_at"));
                p.setStartedAt(toLocalDateTime(rs.getTimestamp("started_at")));
                p.setCompletedAt(toLocalDateTime(rs.getTimestamp("completed_at")));
                pipelineRepo.save(p);
                count++;
            }
        }
        return count;
    }

    private int migrateSteps() throws SQLException {
        int count = 0;
        // Build a local pipeline ID → pipeline cache to avoid N+1 JPA lookups
        Map<String, DeploymentPipeline> pipelineCache = new HashMap<>();

        try (Connection pg = legacyConnection();
             Statement  st = pg.createStatement();
             ResultSet  rs = st.executeQuery(
                     "SELECT id, pipeline_id, step_type, step_order, status, " +
                     "message, started_at, completed_at " +
                     "FROM pipeline_step")) {

            while (rs.next()) {
                String pipelineId = rs.getString("pipeline_id");
                DeploymentPipeline pipeline = pipelineCache.computeIfAbsent(
                        pipelineId, id -> pipelineRepo.findById(id).orElse(null));
                if (pipeline == null) continue;

                PipelineStep step = new PipelineStep();
                step.setId(rs.getString("id"));
                step.setPipeline(pipeline);
                step.setStepType(StepType.valueOf(rs.getString("step_type")));
                step.setStepOrder(rs.getInt("step_order"));
                step.setStatus(StepStatus.valueOf(rs.getString("status")));
                step.setMessage(rs.getString("message"));
                step.setStartedAt(toLocalDateTime(rs.getTimestamp("started_at")));
                step.setCompletedAt(toLocalDateTime(rs.getTimestamp("completed_at")));
                stepRepo.save(step);
                count++;
            }
        }
        return count;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Open a JDBC connection to the legacy Postgres. */
    private Connection legacyConnection() throws SQLException {
        return DriverManager.getConnection(LEGACY_JDBC_URL, LEGACY_USER, LEGACY_PASSWORD);
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket ignored = new Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    // Reflection-free setters to bypass @PrePersist defaults when restoring historical timestamps

    private void setCreatedAt(DeploymentConfig c, Timestamp ts) {
        if (ts != null) c.setCreatedAt(ts.toLocalDateTime());
    }
    private void setUpdatedAt(DeploymentConfig c, Timestamp ts) {
        if (ts != null) c.setUpdatedAt(ts.toLocalDateTime());
    }
    private void setContainerCreatedAt(DeployedContainer dc, Timestamp ts) {
        if (ts != null) dc.setCreatedAt(ts.toLocalDateTime());
    }
    private void setContainerUpdatedAt(DeployedContainer dc, Timestamp ts) {
        if (ts != null) dc.setUpdatedAt(ts.toLocalDateTime());
    }
    private void setPipelineCreatedAt(DeploymentPipeline p, Timestamp ts) {
        if (ts != null) p.setCreatedAt(ts.toLocalDateTime());
    }

    private void writeMarker(String reason) {
        try {
            Files.createDirectories(MARKER.getParent());
            Files.writeString(MARKER, reason + " at " + LocalDateTime.now());
        } catch (IOException e) {
            log.warn("SystemDbMigrator: could not write marker file: {}", e.getMessage());
        }
    }
}

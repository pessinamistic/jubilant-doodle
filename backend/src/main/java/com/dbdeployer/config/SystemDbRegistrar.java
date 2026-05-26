package com.dbdeployer.config;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;

/**
 * Runs at startup ({@code @Order(1)}).
 * Upserts the embedded H2 system database into the {@code deployment_config} /
 * {@code deployed_container} tables so it appears in the UI as a read-only SYSTEM
 * entry with live metadata (H2 version, uptime, file size, etc.).
 * Uses a fixed config ID ("system") so subsequent starts simply update the row.
 * No Docker interaction is required.
 */
@Component
@Order(1)
public class SystemDbRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemDbRegistrar.class);
    static final String SYSTEM_CONFIG_ID = "system";

    private final DeploymentConfigRepository  configRepo;
    private final DeployedContainerRepository containerRepo;
    private final JdbcTemplate                jdbc;

    public SystemDbRegistrar(DeploymentConfigRepository configRepo,
                             DeployedContainerRepository containerRepo,
                             JdbcTemplate jdbc) {
        this.configRepo    = configRepo;
        this.containerRepo = containerRepo;
        this.jdbc          = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Read the real H2 version from the database
            String h2Version = jdbc.queryForObject("SELECT H2VERSION()", String.class);

            // ── Upsert DeploymentConfig ──
            DeploymentConfig config = configRepo.findById(SYSTEM_CONFIG_ID).orElse(null);
            if (config == null) {
                config = new DeploymentConfig();
                config.setId(SYSTEM_CONFIG_ID);
            }
            config.setName("System Database");
            config.setDbType(DbType.H2);
            config.setVersion(h2Version != null ? h2Version : "2.x");
            config.setHostPort(-1);          // embedded — no external port
            config.setContainerPort(-1);
            config.setUsername(null);
            config.setPassword(null);
            config.setDatabaseName("dbdeployer");
            config.setDeployMethod(DeployMethod.EMBEDDED);
            config.setSystem(true);
            configRepo.save(config);

            // ── Upsert DeployedContainer ──
            DeployedContainer container = containerRepo.findByConfigId(SYSTEM_CONFIG_ID).orElse(null);
            if (container == null) {
                container = new DeployedContainer();
                container.setId(UUID.randomUUID().toString());
                container.setConfig(config);
            }
            // No Docker container ID — syncStatuses() checks (containerId == null) and skips safely
            container.setContainerId(null);
            container.setContainerName("h2-embedded");
            container.setStatus(InstanceStatus.RUNNING);
            container.setStartedAt(Instant.now());
            containerRepo.save(container);

            log.info("System DB (H2 embedded, version {}) registered", h2Version);

        } catch (Exception e) {
            // Non-fatal: the app should still start even if registration fails
            log.warn("SystemDbRegistrar: failed to register system DB — {}", e.getMessage());
        }
    }
}

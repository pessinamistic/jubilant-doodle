package com.dbdeployer.config;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.*;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Runs after the Spring context is fully started.
 *
 * Upserts the system Postgres container ("dbdeployer-system-db") into the
 * {@code deployment_config} / {@code deployed_container} tables so it appears
 * in the UI as a read-only SYSTEM entry.
 *
 * Uses a fixed config ID ("system") so subsequent starts simply update the row.
 */
@Component
@Order(2)
public class SystemDbRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemDbRegistrar.class);
    static final String SYSTEM_CONFIG_ID = "system";

    @Value("${dbdeployer.system-db.container-name:dbdeployer-system-db}")
    private String containerName;

    @Value("${dbdeployer.system-db.username:dbdeployer}")
    private String username;

    @Value("${dbdeployer.system-db.password:dbdeployer_internal}")
    private String password;

    @Value("${dbdeployer.system-db.database:dbdeployer}")
    private String database;

    @Value("${dbdeployer.system-db.host-port:5499}")
    private int hostPort;

    private final DeploymentConfigRepository  configRepo;
    private final DeployedContainerRepository containerRepo;
    private final DockerDeployEngine          docker;

    public SystemDbRegistrar(DeploymentConfigRepository configRepo,
                             DeployedContainerRepository containerRepo,
                             DockerDeployEngine docker) {
        this.configRepo    = configRepo;
        this.containerRepo = containerRepo;
        this.docker        = docker;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String containerId = docker.getContainerId(containerName);
            if (containerId == null) {
                log.warn("SystemDbRegistrar: container '{}' not found — skipping registration", containerName);
                return;
            }

            LocalDateTime startedAt = docker.getStartedAt(containerId);

            // ── Upsert DeploymentConfig ──
            DeploymentConfig config = configRepo.findById(SYSTEM_CONFIG_ID).orElse(null);
            if (config == null) {
                config = new DeploymentConfig();
                config.setId(SYSTEM_CONFIG_ID);
            }
            config.setName("System Database");
            config.setDbType(DbType.POSTGRESQL);
            config.setVersion("16");
            config.setHostPort(hostPort);
            config.setContainerPort(5432);
            config.setUsername(username);
            config.setPassword(password);
            config.setDatabaseName(database);
            config.setDeployMethod(DeployMethod.DOCKER);
            config.setSystem(true);
            configRepo.save(config);

            // ── Upsert DeployedContainer ──
            DeployedContainer container = containerRepo.findByConfigId(SYSTEM_CONFIG_ID).orElse(null);
            if (container == null) {
                container = new DeployedContainer();
                container.setId(UUID.randomUUID().toString());
                container.setConfig(config);
            }
            container.setContainerId(containerId);
            container.setContainerName(containerName);
            container.setStatus(InstanceStatus.RUNNING);
            container.setStartedAt(startedAt);
            containerRepo.save(container);

            log.info("System DB registered (containerId={})", containerId.substring(0, 12));

        } catch (Exception e) {
            // Non-fatal: the app should still start even if registration fails
            log.warn("SystemDbRegistrar: failed to register system DB — {}", e.getMessage());
        }
    }
}

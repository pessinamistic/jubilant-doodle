package com.dbdeployer.config;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.*;
import com.dbdeployer.store.DbInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Runs after Spring context is fully started.
 *
 * Upserts the system Postgres container ("dbdeployer-system-db") into the
 * db_instances table so it appears in the UI as a read-only SYSTEM entry.
 *
 * Uses a fixed ID ("system") so subsequent starts simply update the row.
 */
@Component
public class SystemDbRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemDbRegistrar.class);
    static final String SYSTEM_DB_ID = "system";

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

    private final DbInstanceRepository repo;
    private final DockerDeployEngine docker;

    public SystemDbRegistrar(DbInstanceRepository repo, DockerDeployEngine docker) {
        this.repo   = repo;
        this.docker = docker;
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

            DbInstance entry = repo.findById(SYSTEM_DB_ID).orElse(null);
            boolean isNew = entry == null;

            if (isNew) {
                entry = new DbInstance();
                entry.setId(SYSTEM_DB_ID);
            }

            entry.setName("System Database");
            entry.setDbType(DbType.POSTGRESQL);
            entry.setVersion("16");
            entry.setHostPort(hostPort);
            entry.setContainerPort(5432);
            entry.setUsername(username);
            entry.setPassword(password);
            entry.setDatabaseName(database);
            entry.setContainerId(containerId);
            entry.setContainerName(containerName);
            entry.setStatus(InstanceStatus.RUNNING);
            entry.setDeployMethod(DeployMethod.DOCKER);
            entry.setSystem(true);
            entry.setStartedAt(startedAt);

            if (!isNew) {
                // @PreUpdate handles updatedAt; bypass @PrePersist by using save directly
                entry.setUpdatedAt(LocalDateTime.now());
            }

            repo.save(entry);
            log.info("System DB registered in db_instances (containerId={})", containerId.substring(0, 12));

        } catch (Exception e) {
            // Non-fatal: the app should still start even if registration fails
            log.warn("SystemDbRegistrar: failed to register system DB — {}", e.getMessage());
        }
    }
}

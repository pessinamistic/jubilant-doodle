package com.dbdeployer.service;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DbInstance;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.store.DbInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs the Docker deploy pipeline on a Spring-managed async thread pool.
 * Kept in a separate bean so that @Async proxy interception works correctly —
 * self-invocation within the same bean bypasses the proxy.
 */
@Service
public class AsyncDeployer {

    private static final Logger log = LoggerFactory.getLogger(AsyncDeployer.class);

    private final DbInstanceRepository repo;
    private final DockerDeployEngine   docker;

    public AsyncDeployer(DbInstanceRepository repo, DockerDeployEngine docker) {
        this.repo   = repo;
        this.docker = docker;
    }

    @Async
    public void deploy(String instanceId) {
        DbInstance instance = repo.findById(instanceId)
                .orElseThrow(() -> new IllegalStateException("Instance not found: " + instanceId));
        try {
            log.info("Async deploy starting for instance {} ({})", instance.getName(), instanceId);
            instance = docker.deploy(instance);
            instance.setStatus(InstanceStatus.RUNNING);
            log.info("Async deploy complete for instance {} ({})", instance.getName(), instanceId);
        } catch (Exception e) {
            log.error("Async deploy failed for instance {} ({}): {}", instance.getName(), instanceId, e.getMessage(), e);
            instance.setStatus(InstanceStatus.ERROR);
        }
        repo.save(instance);
    }
}

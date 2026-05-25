package com.dbdeployer.service;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.store.DeployedContainerRepository;
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

    private final DeployedContainerRepository containerRepo;
    private final DockerDeployEngine          docker;

    public AsyncDeployer(DeployedContainerRepository containerRepo, DockerDeployEngine docker) {
        this.containerRepo = containerRepo;
        this.docker        = docker;
    }

    /**
     * Pull image → create → start container.
     * Both objects are passed directly (not re-fetched) to avoid a transaction
     * timing race where the async thread queries the DB before the caller's
     * transaction has committed.
     */
    @Async
    public void deploy(DeploymentConfig config, DeployedContainer container) {
        try {
            log.info("Async deploy starting for '{}' ({})", config.getName(), config.getId());
            docker.deploy(config, container);   // mutates container in-place
            // status + containerId + startedAt are set by DockerDeployEngine.deploy()
            log.info("Async deploy complete for '{}' — container {}", config.getName(),
                    container.getContainerId() != null
                            ? container.getContainerId().substring(0, 12) : "?");
        } catch (Exception e) {
            log.error("Async deploy failed for '{}' ({}): {}",
                    config.getName(), config.getId(), e.getMessage(), e);
            container.setStatus(InstanceStatus.ERROR);
        }
        containerRepo.save(container);
    }
}

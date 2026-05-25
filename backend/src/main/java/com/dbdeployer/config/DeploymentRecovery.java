package com.dbdeployer.config;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.store.DeployedContainerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Startup recovery pass that resolves containers stuck in {@link InstanceStatus#DEPLOYING}.
 *
 * This can happen when:
 * <ul>
 *   <li>The application crashed / was restarted while an async deploy was in flight.</li>
 *   <li>The async thread threw an exception before it could write the final status back.</li>
 * </ul>
 *
 * Recovery logic per stuck container:
 * <ul>
 *   <li>{@code containerId == null} — Docker container was never created; mark {@code ERROR}.</li>
 *   <li>{@code containerId != null} — container may have been created; ask Docker for current
 *       state and use that as the ground truth.</li>
 * </ul>
 *
 * Runs after {@link SystemDbRegistrar} (Order 3).
 */
@Component
@Order(3)
public class DeploymentRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeploymentRecovery.class);

    private final DeployedContainerRepository containerRepo;
    private final DockerDeployEngine          docker;

    public DeploymentRecovery(DeployedContainerRepository containerRepo,
                              DockerDeployEngine docker) {
        this.containerRepo = containerRepo;
        this.docker        = docker;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<DeployedContainer> stuck = containerRepo.findByStatus(InstanceStatus.DEPLOYING);
        if (stuck.isEmpty()) return;

        log.info("DeploymentRecovery: found {} container(s) stuck in DEPLOYING — resolving...",
                stuck.size());

        for (DeployedContainer container : stuck) {
            String name = container.getConfig() != null
                    ? container.getConfig().getName() : container.getId();

            if (container.getContainerId() == null) {
                // Async deploy never got as far as creating the container
                log.warn("DeploymentRecovery: '{}' has no containerId — marking ERROR", name);
                container.setStatus(InstanceStatus.ERROR);
            } else {
                // Container may have been created; ask Docker for truth
                InstanceStatus actual = docker.getStatus(container);
                log.info("DeploymentRecovery: '{}' has containerId — Docker reports {}", name, actual);
                container.setStatus(actual);

                // Capture startedAt if it's now running and we don't have it yet
                if (actual == InstanceStatus.RUNNING && container.getStartedAt() == null) {
                    container.setStartedAt(docker.getStartedAt(container.getContainerId()));
                }
            }

            containerRepo.save(container);
        }

        log.info("DeploymentRecovery: done");
    }
}

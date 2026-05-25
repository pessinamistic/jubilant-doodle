package com.dbdeployer.pipeline.step;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.pipeline.model.DeployErrorCode;
import com.dbdeployer.pipeline.model.StepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Step 2 — Create the Docker container (without starting it).
 * Populates {@code container.containerId}, {@code containerName}, and
 * {@code dataDirectory}.
 */
@Component
public class ContainerCreateStep implements DeployStep {

    private static final Logger log = LoggerFactory.getLogger(ContainerCreateStep.class);

    private final DockerDeployEngine docker;

    public ContainerCreateStep(DockerDeployEngine docker) {
        this.docker = docker;
    }

    @Override
    public StepType type() {
        return StepType.CREATE_CONTAINER;
    }

    @Override
    public String execute(DeploymentConfig config, DeployedContainer container)
            throws StepExecutionException {
        log.info("[pipeline] Creating container for '{}'", config.getName());
        try {
            docker.createContainer(config, container);
        } catch (com.github.dockerjava.api.exception.ConflictException e) {
            throw new StepExecutionException(
                    DeployErrorCode.CONTAINER_NAME_CONFLICT,
                    "Container name conflict: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new StepExecutionException(
                    DeployErrorCode.VOLUME_CREATE_FAILED,
                    "Failed to create data directory: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new StepExecutionException(
                    DeployErrorCode.CONTAINER_CREATE_FAILED,
                    "Failed to create container: " + e.getMessage(), e);
        }
        return "Created container: " + container.getContainerName()
                + " (" + container.getContainerId().substring(0, 12) + ")";
    }
}

package com.dbdeployer.pipeline.step;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.pipeline.model.DeployErrorCode;
import com.dbdeployer.pipeline.model.StepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Step 3 — Start the previously-created Docker container. */
@Component
public class ContainerStartStep implements DeployStep {

    private static final Logger log = LoggerFactory.getLogger(ContainerStartStep.class);

    private final DockerDeployEngine docker;

    public ContainerStartStep(DockerDeployEngine docker) {
        this.docker = docker;
    }

    @Override
    public StepType type() {
        return StepType.START_CONTAINER;
    }

    @Override
    public String execute(DeploymentConfig config, DeployedContainer container) throws StepExecutionException {
        log.info("[pipeline] Starting container '{}'", container.getContainerName());
        try {
            docker.startContainer(container);
        } catch (com.github.dockerjava.api.exception.NotModifiedException e) {
            // Container is already running — treat as success
            log.warn("[pipeline] Container already running: {}", container.getContainerName());
        } catch (Exception e) {
            throw new StepExecutionException(
                    DeployErrorCode.CONTAINER_START_FAILED, "Failed to start container: " + e.getMessage(), e);
        }
        return "Container started: " + container.getContainerName();
    }
}

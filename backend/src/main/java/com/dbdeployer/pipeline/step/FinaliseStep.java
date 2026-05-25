package com.dbdeployer.pipeline.step;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.pipeline.model.StepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Step 4 — Finalise the deployment: verify the container is still running and
 * capture the {@code startedAt} timestamp from Docker.
 */
@Component
public class FinaliseStep implements DeployStep {

    private static final Logger log = LoggerFactory.getLogger(FinaliseStep.class);

    private final DockerDeployEngine docker;

    public FinaliseStep(DockerDeployEngine docker) {
        this.docker = docker;
    }

    @Override
    public StepType type() {
        return StepType.FINALISE;
    }

    @Override
    public String execute(DeploymentConfig config, DeployedContainer container)
            throws StepExecutionException {
        InstanceStatus status = docker.getStatus(container);
        log.info("[pipeline] Finalise — Docker reports status {}", status);

        container.setStatus(status);
        if (status == InstanceStatus.RUNNING) {
            var startedAt = docker.getStartedAt(container.getContainerId());
            if (startedAt != null) container.setStartedAt(startedAt);
        }

        if (status != InstanceStatus.RUNNING) {
            throw new StepExecutionException(
                    com.dbdeployer.pipeline.model.DeployErrorCode.CONTAINER_EXITED_IMMEDIATELY,
                    "Container exited immediately after start — status: " + status);
        }

        return "Container is RUNNING";
    }
}

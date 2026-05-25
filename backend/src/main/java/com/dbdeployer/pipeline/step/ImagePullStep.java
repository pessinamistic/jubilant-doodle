package com.dbdeployer.pipeline.step;

import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.pipeline.model.DeployErrorCode;
import com.dbdeployer.pipeline.model.StepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Step 1 — Pull (or verify) the Docker image required for this deployment.
 */
@Component
public class ImagePullStep implements DeployStep {

    private static final Logger log = LoggerFactory.getLogger(ImagePullStep.class);

    private final DockerDeployEngine docker;

    public ImagePullStep(DockerDeployEngine docker) {
        this.docker = docker;
    }

    @Override
    public StepType type() {
        return StepType.PULL_IMAGE;
    }

    @Override
    public String execute(DeploymentConfig config, DeployedContainer container)
            throws StepExecutionException {
        var def   = DatabaseCatalog.get(config.getDbType());
        String image = def.dockerImage() + ":" + config.getVersion();
        log.info("[pipeline] Pulling image: {}", image);
        try {
            docker.pullImage(image);
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            throw new StepExecutionException(
                    DeployErrorCode.IMAGE_NOT_FOUND,
                    "Image not found: " + image, e);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new StepExecutionException(
                    DeployErrorCode.IMAGE_PULL_TIMEOUT,
                    "Timed out pulling image: " + image, e);
        } catch (Exception e) {
            throw new StepExecutionException(
                    DeployErrorCode.IMAGE_PULL_FAILED,
                    "Failed to pull image " + image + ": " + e.getMessage(), e);
        }
        return "Pulled image: " + image;
    }
}

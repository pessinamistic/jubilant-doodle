package com.dbdeployer.pipeline.step;

import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.pipeline.model.DeployErrorCode;
import com.dbdeployer.pipeline.model.StepType;
import com.dbdeployer.service.ImageValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Step 1 — Pull (or verify) the Docker image required for this deployment. */
@Slf4j
@Component
public class ImagePullStep implements DeployStep {

  private final DockerDeployEngine docker;
  private final ImageValidationService imageValidationService;

  public ImagePullStep(DockerDeployEngine docker, ImageValidationService imageValidationService) {
    this.docker = docker;
    this.imageValidationService = imageValidationService;
  }

  @Override
  public StepType type() {
    return StepType.PULL_IMAGE;
  }

  @Override
  public String execute(DeploymentConfig config, DeployedContainer container)
      throws StepExecutionException {
    var def = DatabaseCatalog.get(config.getDbType());
    String imageName = def.dockerImage();
    String tag = config.getVersion();
    String image = imageName + ":" + tag;

    log.info("[pipeline] Ensuring image is available locally: {}", image);
    try {
      boolean pulled = docker.ensureImageAvailable(imageName, tag);

      // Best-effort image tracking sync for image management views.
      try {
        imageValidationService.refreshLocalOnly(config.getDbType(), tag);
      } catch (Exception trackingEx) {
        log.warn(
            "[pipeline] Local image tracking sync skipped for {}: {}",
            image,
            trackingEx.getMessage());
      }

      if (pulled) {
        log.info("[pipeline] Image pulled for deployment: {}", image);
        return "Pulled image: " + image;
      }
      log.info("[pipeline] Image already local; pull skipped: {}", image);
      return "Image already available locally: " + image;
    } catch (com.github.dockerjava.api.exception.NotFoundException e) {
      throw new StepExecutionException(
          DeployErrorCode.IMAGE_NOT_FOUND, "Image not found: " + image, e);
    } catch (java.util.concurrent.TimeoutException e) {
      throw new StepExecutionException(
          DeployErrorCode.IMAGE_PULL_TIMEOUT, "Timed out pulling image: " + image, e);
    } catch (Exception e) {
      throw new StepExecutionException(
          DeployErrorCode.IMAGE_PULL_FAILED,
          "Failed to pull image " + image + ": " + e.getMessage(),
          e);
    }
  }
}

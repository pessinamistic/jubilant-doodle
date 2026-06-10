package com.dbdeployer.validations;

import com.dbdeployer.api.dto.DeployRequest;
import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.model.ImageValidationDecision;
import com.dbdeployer.service.ImageValidationService;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeploymentValidations {

  private final DeploymentConfigRepository configRepo;
  private final ImageValidationService imageValidation;
  private final DeployedContainerRepository containerRepo;

  public DeploymentValidations(
    DeploymentConfigRepository configRepo,
    ImageValidationService imageValidation,
    DeployedContainerRepository containerRepo) {
    this.configRepo = configRepo;
    this.imageValidation = imageValidation;
    this.containerRepo = containerRepo;
  }

  // @Override
  public boolean validate(
    DeployRequest deployRequest,
    boolean isTemplate) {
    if (configRepo.existsByName(deployRequest.name())) {
      log.error("[deploy] Rejecting request: name already exists '{}'", deployRequest.name());
      throw new IllegalArgumentException("An instance named '" + deployRequest.name() + "' already exists");
    }

    if (containerRepo.existsByContainerName(deployRequest.name())) {
      log.error("[deploy] Rejecting request: name already exists '{}'", deployRequest.name());
      throw new IllegalArgumentException("An instance named '" + deployRequest.name() + "' already exists");
    }

    if (containerRepo.existsByHostPortAndNotRemoved(deployRequest.hostPort())) {
      log.error("[deploy] Rejecting request: host port {} already in use", deployRequest.hostPort());
      throw new IllegalArgumentException("Port " + deployRequest.hostPort() + " is already in use");
    }

    var def = DatabaseCatalog.get(deployRequest.dbType());
    if (def == null) {
      log.warn("[deploy] Rejecting request: unsupported database type {}", deployRequest.dbType());
      throw new IllegalArgumentException("Unsupported database type: " + deployRequest.dbType());
    }

    // Validate image availability before creating any deployment or pipeline rows.
    var imageCheck = imageValidation.checkForDeploy(deployRequest.dbType(), deployRequest.version());
    log.info("[deploy] Image check result for {}:{} -> decision={}, local={}, hub={}, message='{}'", def.dockerImage(),
        deployRequest.version(), imageCheck.decision(), imageCheck.localStatus(), imageCheck.dockerHubStatus(),
        imageCheck.message());
    if (imageCheck.decision() == ImageValidationDecision.BLOCK) {
      log.warn("[deploy] Blocking deployment '{}' because image is not deployable: {}", deployRequest.name(),
          imageCheck.message());
      throw new IllegalArgumentException(imageCheck.message());
    }
    if (imageCheck.decision() == ImageValidationDecision.ALLOW_WITH_WARNING) {
      log.warn("Proceeding with deployment '{}' despite warning: {}", deployRequest.name(), imageCheck.message());
    }
    return true;
  }
}

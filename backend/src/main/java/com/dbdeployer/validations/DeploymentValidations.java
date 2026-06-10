package com.dbdeployer.validations;

import com.dbdeployer.api.dto.DeployRequest;
import com.dbdeployer.service.ImageValidationService;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeploymentValidations implements ValidationInterface {

  private final DeploymentConfigRepository configRepo;
  private final ImageValidationService imageValidation;
  private final DeployedContainerRepository containerRepo;

  public DeploymentValidations(DeploymentConfigRepository configRepo, ImageValidationService imageValidation,
      ImageValidationService imageValidation1, ImageValidationService imageValidation2,
      DeployedContainerRepository containerRepo) {
    this.configRepo = configRepo;
    this.imageValidation = imageValidation2;
    this.containerRepo = containerRepo;
  }

  @Override
  public boolean validate(DeployRequest deployRequest) {
    if (configRepo.existsByName(deployRequest.name())) {
      log.error("[deploy] Rejecting request: name already exists '{}'", deployRequest.name());
      throw new IllegalArgumentException("An instance named '" + deployRequest.name() + "' already exists");
    }

    if (containerRepo.existsByName(deployRequest.name())) {
      log.error("[deploy] Rejecting request: name already exists '{}'", deployRequest.name());
      throw new IllegalArgumentException("An instance named '" + deployRequest.name() + "' already exists");
    }

    if (containerRepo.existsByHostPortAndNotRemoved(deployRequest.hostPort())) {
      log.error("[deploy] Rejecting request: host port {} already in use", deployRequest.hostPort());
      throw new IllegalArgumentException("Port " + deployRequest.hostPort() + " is already in use");
    }
    return true;
  }
}

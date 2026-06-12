package com.dbdeployer.service;

import com.dbdeployer.api.dto.ConfigTemplateRequest;
import com.dbdeployer.api.dto.DeployFromTemplateRequest;
import com.dbdeployer.api.dto.DeployRequest;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.DeploymentResponse;
import com.dbdeployer.store.DeploymentConfigRepository;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ConfigTemplateService {

  private final DbInstanceService instanceService;
  private final DeploymentConfigRepository configRepo;

  public ConfigTemplateService(DbInstanceService instanceService,
                               DeploymentConfigRepository configRepo) {
    this.instanceService = instanceService;
    this.configRepo = configRepo;
  }

  public List<DeploymentConfig> listAll() {
    return configRepo.findAllByIsTemplateTrueOrderByCreatedAtDesc();
  }

  public DeploymentConfig getById(String id, boolean isTemplate) {
    return configRepo.findByIdAndIsTemplate(id, isTemplate)
        .orElseThrow(() -> new IllegalArgumentException("Configuration template not found: " + id));
  }

  @Transactional
  public DeploymentConfig create(
    ConfigTemplateRequest req) {
    if (configRepo.existsByName(req.name())) {
      throw new IllegalArgumentException("A configuration named '" + req.name() + "' already exists");
    }
    DeploymentConfig deploymentConfig = new DeploymentConfig();
    deploymentConfig.setId(UUID.randomUUID().toString());
    deploymentConfig.setTemplate(true);
    applyRequest(deploymentConfig, req);
    return configRepo.save(deploymentConfig);
  }

  @Transactional
  public DeploymentConfig update(
    String id,
    ConfigTemplateRequest req) {
    DeploymentConfig t = getById(id, true);
    if (configRepo.existsByNameAndIsTemplateTrueAndIdNot(req.name(), id)) {
      throw new IllegalArgumentException("A template named '" + req.name() + "' already exists");
    }
    applyRequest(t, req);
    return configRepo.save(t);
  }

  @Transactional
  public void delete(
    String id) {
    DeploymentConfig t = getById(id,  true);
    configRepo.delete(t);
  }

  /**
   * Deploy a new instance from a saved template. The caller supplies a unique
   * instance name and the desired host port (the two per-deployment overrides).
   * All other fields come from the template. Increments deployCount on the
   * template after a successful dispatch.
   */
  @Transactional
  public DeploymentResponse deployFromTemplate(
    String templateId,
    DeployFromTemplateRequest req) {
    DeploymentConfig deploymentConfig = getById(templateId, true);

    DeployRequest deployReq = new DeployRequest(req.instanceName(),
        deploymentConfig.getDbType(),
        deploymentConfig.getVersion(),
        req.hostPort(),
        deploymentConfig.getUsername(),
        deploymentConfig.getPassword(),
        deploymentConfig.getDatabaseName(),
        deploymentConfig.getExtraEnvJson());

    log.info("Deploying new instance from template {} with request {}", templateId, deployReq);

    DeploymentResponse deploymentResponse = instanceService.deploy(deployReq, templateId, true);

    deploymentConfig.setDeployCount(deploymentConfig.getDeployCount() + 1);
    DeploymentConfig save = configRepo.save(deploymentConfig);

    deploymentResponse.setDeploymentConfig(save);
    return deploymentResponse;
  }

  private void applyRequest(
    DeploymentConfig deploymentConfig,
    ConfigTemplateRequest req) {
    deploymentConfig.setName(req.name());
    deploymentConfig.setDescription(req.description());
    deploymentConfig.setDbType(req.dbType());
    deploymentConfig.setVersion(req.version());
    deploymentConfig.setHostPort(req.hostPort());
    deploymentConfig.setUsername(req.username());
    deploymentConfig.setPassword(req.password());
    deploymentConfig.setDatabaseName(req.databaseName());
    deploymentConfig.setExtraEnvJson(req.extraEnvJson());
  }
}

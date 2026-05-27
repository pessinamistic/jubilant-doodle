package com.dbdeployer.service;

import com.dbdeployer.api.dto.ConfigTemplateRequest;
import com.dbdeployer.api.dto.DeployFromTemplateRequest;
import com.dbdeployer.api.dto.DeployRequest;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.store.DeploymentConfigRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigTemplateService {

  private final DeploymentConfigRepository configRepo;
  private final DbInstanceService instanceService;

  public ConfigTemplateService(
      DeploymentConfigRepository configRepo, DbInstanceService instanceService) {
    this.configRepo = configRepo;
    this.instanceService = instanceService;
  }

  public List<DeploymentConfig> listAll() {
    return configRepo.findAllByIsTemplateTrueOrderByCreatedAtDesc();
  }

  public DeploymentConfig getById(String id) {
    return configRepo
        .findByIdAndIsTemplateTrue(id)
        .orElseThrow(() -> new IllegalArgumentException("Configuration template not found: " + id));
  }

  @Transactional
  public DeploymentConfig create(ConfigTemplateRequest req) {
    if (configRepo.existsByName(req.name())) {
      throw new IllegalArgumentException(
          "A configuration named '" + req.name() + "' already exists");
    }
    DeploymentConfig t = new DeploymentConfig();
    t.setId(UUID.randomUUID().toString());
    t.setTemplate(true);
    applyRequest(t, req);
    return configRepo.save(t);
  }

  @Transactional
  public DeploymentConfig update(String id, ConfigTemplateRequest req) {
    DeploymentConfig t = getById(id);
    if (configRepo.existsByNameAndIsTemplateTrueAndIdNot(req.name(), id)) {
      throw new IllegalArgumentException("A template named '" + req.name() + "' already exists");
    }
    applyRequest(t, req);
    return configRepo.save(t);
  }

  @Transactional
  public void delete(String id) {
    DeploymentConfig t = getById(id);
    configRepo.delete(t);
  }

  /**
   * Deploy a new instance from a saved template. The caller supplies a unique instance name and the
   * desired host port (the two per-deployment overrides). All other fields come from the template.
   * Increments deployCount on the template after a successful dispatch.
   */
  @Transactional
  public DeploymentConfig deployFromTemplate(String templateId, DeployFromTemplateRequest req) {
    DeploymentConfig t = getById(templateId);

    DeployRequest deployReq =
        new DeployRequest(
            req.instanceName(),
            t.getDbType(),
            t.getVersion(),
            req.hostPort(),
            t.getUsername(),
            t.getPassword(),
            t.getDatabaseName(),
            t.getExtraEnvJson());

    DeploymentConfig config = instanceService.deploy(deployReq, templateId);

    t.setDeployCount(t.getDeployCount() + 1);
    configRepo.save(t);

    return config;
  }

  private void applyRequest(DeploymentConfig t, ConfigTemplateRequest req) {
    t.setName(req.name());
    t.setDescription(req.description());
    t.setDbType(req.dbType());
    t.setVersion(req.version());
    t.setHostPort(req.hostPort());
    t.setUsername(req.username());
    t.setPassword(req.password());
    t.setDatabaseName(req.databaseName());
    t.setExtraEnvJson(req.extraEnvJson());
  }
}

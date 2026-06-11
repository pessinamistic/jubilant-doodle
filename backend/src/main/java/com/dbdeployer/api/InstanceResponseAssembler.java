package com.dbdeployer.api;

import com.dbdeployer.api.dto.InstanceResponse;
import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.DeploymentResponse;
import com.dbdeployer.store.DeploymentConfigRepository;
import org.springframework.stereotype.Component;

/** Maps deployment entities into API-facing instance DTOs. */
@Component
public class InstanceResponseAssembler {

  private final ConnectionStringBuilder connBuilder;
  private final DeploymentConfigRepository configRepo;

  public InstanceResponseAssembler(
    ConnectionStringBuilder connBuilder,
    DeploymentConfigRepository configRepo) {
    this.connBuilder = connBuilder;
    this.configRepo = configRepo;
  }

  public InstanceResponse fromConfig(
    DeploymentResponse deploymentResponse) {
    return build(deploymentResponse.getDeploymentConfig(), deploymentResponse.getDeployedContainer());
  }

  public InstanceResponse fromContainer(
    DeployedContainer container) {
    return build(container.getConfig(), container);
  }

  private InstanceResponse build(
    DeploymentConfig config,
    DeployedContainer container) {
    var def = DatabaseCatalog.get(config.getDbType());
    String display = def != null ? def.displayName() : config.getDbType().name();
    String icon = def != null ? def.icon() : "🗄️";
    String conn = connBuilder.build(config);
    String masked = connBuilder.buildMasked(config);
    String templateId = config.getId();
    String templateName = templateId != null
        ? configRepo.findById(templateId).map(DeploymentConfig::getName).orElse(null)
        : null;

    return InstanceResponse.from(config, container, conn, masked, display, icon, templateId, templateName);
  }
}

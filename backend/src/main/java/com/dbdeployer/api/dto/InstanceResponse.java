package com.dbdeployer.api.dto;

import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import java.time.Instant;

public record InstanceResponse(
    String id,
    String name,
    DbType dbType,
    String dbTypeDisplay,
    String icon,
    String version,
    int hostPort,
    String username,
    String password,
    String databaseName,
    String containerId,
    String containerName,
    InstanceStatus status,
    DeployMethod deployMethod,
    String dataDirectory,
    String connectionString, // real — for copying
    String connectionStringMasked, // masked — for display
    Instant createdAt,
    Instant updatedAt,
    Instant startedAt, // time the container was last started (for uptime)
    boolean isSystem, // true = system DB — hide stop/remove actions
    boolean isImported, // true = imported container — untrack keeps container alive
    String latestPipelineId // ID of the most recent deploy pipeline, if any
    ) {
  /**
   * Build an {@link InstanceResponse} from the two-table model. {@code container} may be {@code
   * null} while a deploy is being set up.
   */
  public static InstanceResponse from(
      DeploymentConfig config,
      DeployedContainer container,
      String connectionString,
      String connectionStringMasked,
      String displayName,
      String icon) {
    return new InstanceResponse(
        config.getId(),
        config.getName(),
        config.getDbType(),
        displayName,
        icon,
        config.getVersion(),
        config.getHostPort(),
        config.getUsername(),
        config.getPassword(),
        config.getDatabaseName(),
        container != null ? container.getContainerId() : null,
        container != null ? container.getContainerName() : null,
        container != null ? container.getStatus() : InstanceStatus.DEPLOYING,
        config.getDeployMethod(),
        container != null ? container.getDataDirectory() : null,
        connectionString,
        connectionStringMasked,
        config.getCreatedAt(),
        config.getUpdatedAt(),
        container != null ? container.getStartedAt() : null,
        config.isSystem(),
        config.isImported(),
        container != null ? container.getLatestPipelineId() : null);
  }
}

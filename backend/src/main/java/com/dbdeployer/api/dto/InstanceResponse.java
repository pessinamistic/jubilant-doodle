package com.dbdeployer.api.dto;

import com.dbdeployer.model.DbInstance;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.InstanceStatus;
import java.time.LocalDateTime;

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
        String connectionString,       // real — for copying
        String connectionStringMasked, // masked — for display
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime startedAt,       // time the container was last started (for uptime)
        boolean isSystem,              // true = system DB — hide stop/remove actions
        boolean isImported             // true = imported container — remove only untracks, does not delete
) {
    public static InstanceResponse from(DbInstance i,
                                        String connectionString,
                                        String connectionStringMasked,
                                        String displayName,
                                        String icon) {
        return new InstanceResponse(
                i.getId(),
                i.getName(),
                i.getDbType(),
                displayName,
                icon,
                i.getVersion(),
                i.getHostPort(),
                i.getUsername(),
                i.getPassword(),
                i.getDatabaseName(),
                i.getContainerId(),
                i.getContainerName(),
                i.getStatus(),
                i.getDeployMethod(),
                i.getDataDirectory(),
                connectionString,
                connectionStringMasked,
                i.getCreatedAt(),
                i.getUpdatedAt(),
                i.getStartedAt(),
                i.isSystem(),
                i.isImported()
        );
    }
}

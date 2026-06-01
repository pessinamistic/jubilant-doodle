package com.dbdeployer.api.dto;

import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeploymentConfig;
import java.time.Instant;

public record ConfigTemplateResponse(
        String id,
        String name,
        String description,
        DbType dbType,
        String dbTypeDisplay,
        String icon,
        String version,
        int hostPort,
        String username,
        String password,
        String databaseName,
        String extraEnvJson,
        int deployCount,
        Instant createdAt,
        Instant updatedAt) {

    public static ConfigTemplateResponse from(DeploymentConfig t) {
        var def = DatabaseCatalog.get(t.getDbType());
        String display = def != null ? def.displayName() : t.getDbType().name();
        String icon = def != null ? def.icon() : "🗄️";
        return new ConfigTemplateResponse(
                t.getId(),
                t.getName(),
                t.getDescription(),
                t.getDbType(),
                display,
                icon,
                t.getVersion(),
                t.getHostPort(),
                t.getUsername(),
                t.getPassword(),
                t.getDatabaseName(),
                t.getExtraEnvJson(),
                t.getDeployCount(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}

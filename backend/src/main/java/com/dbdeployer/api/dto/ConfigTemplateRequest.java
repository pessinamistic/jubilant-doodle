package com.dbdeployer.api.dto;

import com.dbdeployer.model.DbType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfigTemplateRequest(
    @NotBlank String name,
    String description,
    @NotNull DbType dbType,
    @NotBlank String version,
    @Min(1024) @Max(65535) int hostPort,
    String username,
    String password,
    String databaseName,
    String extraEnvJson
) {
}

package com.dbdeployer.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DeployFromTemplateRequest(
    @NotBlank String instanceName,
    @Min(1024) @Max(65535) int hostPort) {}

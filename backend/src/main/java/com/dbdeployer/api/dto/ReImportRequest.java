package com.dbdeployer.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReImportRequest(
    @NotBlank String containerId,
    @NotBlank String containerName) {
}

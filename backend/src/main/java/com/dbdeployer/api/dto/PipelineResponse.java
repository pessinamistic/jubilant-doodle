package com.dbdeployer.api.dto;

import com.dbdeployer.pipeline.model.DeployErrorCode;
import com.dbdeployer.pipeline.model.DeploymentPipeline;
import com.dbdeployer.pipeline.model.PipelineStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PipelineResponse(
        String id,
        String configId,
        PipelineStatus status,
        DeployErrorCode errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        List<PipelineStepResponse> steps
) {
    public static PipelineResponse from(DeploymentPipeline p, List<PipelineStepResponse> steps) {
        return new PipelineResponse(
                p.getId(),
                p.getConfigId(),
                p.getStatus(),
                p.getErrorCode(),
                p.getErrorMessage(),
                p.getCreatedAt(),
                p.getStartedAt(),
                p.getCompletedAt(),
                steps
        );
    }
}

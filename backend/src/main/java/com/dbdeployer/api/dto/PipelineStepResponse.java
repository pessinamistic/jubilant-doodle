package com.dbdeployer.api.dto;

import com.dbdeployer.pipeline.model.PipelineStep;
import com.dbdeployer.pipeline.model.StepStatus;
import com.dbdeployer.pipeline.model.StepType;

import java.time.LocalDateTime;

public record PipelineStepResponse(
        String id,
        StepType stepType,
        int stepOrder,
        StepStatus status,
        String message,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
    public static PipelineStepResponse from(PipelineStep s) {
        return new PipelineStepResponse(
                s.getId(),
                s.getStepType(),
                s.getStepOrder(),
                s.getStatus(),
                s.getMessage(),
                s.getStartedAt(),
                s.getCompletedAt()
        );
    }
}

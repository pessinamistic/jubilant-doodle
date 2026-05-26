package com.dbdeployer.api.dto;

import java.time.Instant;

import com.dbdeployer.pipeline.model.PipelineStep;
import com.dbdeployer.pipeline.model.StepStatus;
import com.dbdeployer.pipeline.model.StepType;

public record PipelineStepResponse(
        String id,
        StepType stepType,
        int stepOrder,
        StepStatus status,
        String message,
        Instant startedAt,
        Instant completedAt
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

package com.dbdeployer.api.dto;

import java.time.LocalDateTime;

import com.dbdeployer.model.DbType;
import com.dbdeployer.model.ImageAvailabilityState;
import com.dbdeployer.model.ImageValidationDecision;

public record ImageCheckResponse(
        DbType dbType,
        String displayName,
        String image,
        String tag,
        String imageRef,
        boolean dockerHubManaged,
        ImageAvailabilityState localStatus,
        ImageAvailabilityState dockerHubStatus,
        ImageValidationDecision decision,
        String message,
        LocalDateTime localCheckedAt,
        LocalDateTime dockerHubCheckedAt,
        LocalDateTime updatedAt
) {}

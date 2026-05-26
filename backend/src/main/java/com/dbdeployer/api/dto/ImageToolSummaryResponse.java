package com.dbdeployer.api.dto;

import java.time.LocalDateTime;

import com.dbdeployer.model.DbType;

public record ImageToolSummaryResponse(
        DbType dbType,
        String displayName,
        String icon,
        String image,
        int totalTags,
        int allowCount,
        int warningCount,
        int blockedCount,
        int localAvailableCount,
        int dockerHubAvailableCount,
        LocalDateTime updatedAt
) {}

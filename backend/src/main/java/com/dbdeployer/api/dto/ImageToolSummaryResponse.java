package com.dbdeployer.api.dto;

import com.dbdeployer.model.DbType;
import java.time.LocalDateTime;

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
    LocalDateTime updatedAt) {}

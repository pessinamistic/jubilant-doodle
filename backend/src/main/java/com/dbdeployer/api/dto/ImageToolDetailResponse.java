package com.dbdeployer.api.dto;

import com.dbdeployer.model.DbType;
import java.time.LocalDateTime;
import java.util.List;

public record ImageToolDetailResponse(DbType dbType, String displayName, String icon, String image, int totalTags,
    int allowCount, int warningCount, int blockedCount, int localAvailableCount, int dockerHubAvailableCount,
    LocalDateTime updatedAt, List<ImageCheckResponse> tags) {
}

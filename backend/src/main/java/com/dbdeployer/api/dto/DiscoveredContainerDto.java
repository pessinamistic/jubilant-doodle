package com.dbdeployer.api.dto;

import com.dbdeployer.model.DbType;

/**
 * A Docker container that is running but not yet tracked by DB Deployer.
 * Returned by GET /api/instances/discover.
 */
public record DiscoveredContainerDto(
        String containerId,
        String containerName, // Docker name (no leading slash)
        String image,
        DbType suggestedDbType, // null if we couldn't detect
        String suggestedDbTypeDisplay,
        String icon,
        Integer suggestedHostPort, // mapped host port, null if not exposed
        int containerPort,
        String status) {}

package com.dbdeployer.api.dto;

/**
 * Request body for POST /api/instances/import. Registers a pre-existing Docker container as a
 * managed db_instance row.
 */
public record ImportRequest(
    String containerId,
    String containerName,
    String name, // user-chosen display name
    String dbType, // DbType enum name, e.g. "POSTGRESQL"
    int hostPort,
    int containerPort,
    String username,
    String password,
    String databaseName,
    String version) {}

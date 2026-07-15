package com.dbdeployer.ai.tools;

/**
 * Compact summary of one deployed instance for agent/MCP tool results.
 *
 * @param name instance name
 * @param type service type (e.g. POSTGRESQL)
 * @param version image version/tag
 * @param status current lifecycle status
 * @param hostPort published host port
 * @param connectionString masked connection string
 */
public record InstanceSummary(
    String name,
    String type,
    String version,
    String status,
    int hostPort,
    String connectionString) {}

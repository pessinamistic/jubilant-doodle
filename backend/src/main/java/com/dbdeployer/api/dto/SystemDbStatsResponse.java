package com.dbdeployer.api.dto;

import java.util.List;

/**
 * Rich stats snapshot for the Port Wrangler system (Postgres) database.
 * Returned by {@code GET
 * /api/system/stats}.
 */
public record SystemDbStatsResponse(DbInfo db, SchemaInfo schema, PoolInfo pool, AppInfo app, JvmInfo jvm) {

  public record DbInfo(String type, String version, String host, int port, String databaseName, long dbSizeBytes) {
  }

  public record SchemaInfo(int tableCount, List<TableStat> tables) {
  }

  public record TableStat(String tableName, long rowCount) {
  }

  public record PoolInfo(int maxSize, int activeConnections, int idleConnections, int pendingThreads,
      int totalConnections) {
  }

  public record AppInfo(long uptimeSeconds, String startedAt) {
  }

  public record JvmInfo(long heapUsedMb, long heapMaxMb) {
  }
}

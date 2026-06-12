package com.dbdeployer.deploy;

import com.dbdeployer.model.DeploymentConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Collects per-tool live metrics (Postgres connections, Redis ops/sec, Mongo
 * server status, etc.) by running a short, read-only command inside the
 * container with {@code docker exec}.
 *
 * <p>
 * Every probe has a hard 3-second timeout and silently returns an empty map on
 * any failure — metrics are best-effort and must never block the UI.
 *
 * <p>
 * Commands are constructed with constants and credentials drawn from the
 * tracked {@link DeploymentConfig}; no user-controlled string is ever
 * concatenated into a shell, mitigating command injection.
 */
@Slf4j
@Component
public class ToolMetricsProbe {

  private final DockerDeployEngine docker;

  public ToolMetricsProbe(
    @Lazy DockerDeployEngine docker) {
    this.docker = docker;
  }

  /**
   * Returns tool-specific metrics for the given container or an empty map if the
   * database type is not supported, the exec call fails, or output cannot be
   * parsed. Never throws.
   */
  public Map<String, Object> collect(
    DeploymentConfig config,
    String containerId) {
    if (config == null || containerId == null)
      return Map.of();
    try {
      return switch (config.getDbType()) {
        case POSTGRESQL -> probePostgres(config, containerId);
        case MYSQL, MARIADB -> probeMysql(config, containerId);
        case REDIS -> probeRedis(containerId);
        case MONGODB -> probeMongo(config, containerId);
        default -> Map.of();
      };
    } catch (Exception e) {
      log.debug("tool-metrics probe failed for {}: {}", config.getDbType(), e.getMessage());
      return Map.of();
    }
  }

  // ── PostgreSQL ────────────────────────────────────────────────────────
  private Map<String, Object> probePostgres(
    DeploymentConfig cfg,
    String containerId) {
    String user = nonBlank(cfg.getUsername(), "postgres");
    String db = nonBlank(cfg.getDatabaseName(), user);
    String sql = "SELECT (SELECT count(*) FROM pg_stat_activity), "
        + "(SELECT setting::int FROM pg_settings WHERE name='max_connections'), "
        + "(SELECT pg_database_size(current_database())), "
        + "(SELECT count(*) FROM pg_stat_activity WHERE state='active'), "
        + "(SELECT count(*) FROM pg_stat_activity WHERE state='idle'), "
        + "(SELECT extract(epoch from now()-pg_postmaster_start_time())::bigint);";
    String out = docker.execCapture(containerId,
        new String[]{"psql", "-U", user, "-d", db, "-AtXq", "-F", "|", "-c", sql}, EXEC_TIMEOUT_SECONDS);
    if (out == null)
      return Map.of();
    String[] parts = out.trim().split("\\|");
    if (parts.length < 6)
      return Map.of();
    Map<String, Object> m = new LinkedHashMap<>();
    putLong(m, "connections", parts[0]);
    putLong(m, "maxConnections", parts[1]);
    putLong(m, "databaseSizeBytes", parts[2]);
    putLong(m, "activeConnections", parts[3]);
    putLong(m, "idleConnections", parts[4]);
    putLong(m, "serverUptimeSeconds", parts[5]);
    return m;
  }

  // ── MySQL / MariaDB ────────────────────────────────────────────────────
  private Map<String, Object> probeMysql(
    DeploymentConfig cfg,
    String containerId) {
    String user = nonBlank(cfg.getUsername(), "root");
    String pass = nonBlank(cfg.getPassword(), "");
    if (pass.isEmpty())
      return Map.of(); // need credentials
    String out = docker.execCapture(containerId,
        new String[]{"mysql", "-u", user, "-p" + pass, "-N", "-B", "-e",
            "SHOW GLOBAL STATUS WHERE Variable_name IN " + "('Threads_connected','Threads_running','Uptime','Queries',"
                + "'Slow_queries','Aborted_connects','Innodb_buffer_pool_pages_data');"},
        EXEC_TIMEOUT_SECONDS);
    if (out == null)
      return Map.of();
    Map<String, Object> m = new LinkedHashMap<>();
    for (String line : out.split("\\R")) {
      String[] kv = line.split("\\s+");
      if (kv.length < 2)
        continue;
      switch (kv[0]) {
        case "Threads_connected" -> putLong(m, "threadsConnected", kv[1]);
        case "Threads_running" -> putLong(m, "threadsRunning", kv[1]);
        case "Uptime" -> putLong(m, "serverUptimeSeconds", kv[1]);
        case "Queries" -> putLong(m, "totalQueries", kv[1]);
        case "Slow_queries" -> putLong(m, "slowQueries", kv[1]);
        case "Aborted_connects" -> putLong(m, "abortedConnects", kv[1]);
        case "Innodb_buffer_pool_pages_data" -> putLong(m, "innodbBufferPoolPagesData", kv[1]);
        default -> {
          /* ignore */
        }
      }
    }
    return m;
  }

  // ── Redis ─────────────────────────────────────────────────────────────
  private static final Pattern REDIS_KV = Pattern.compile("^([a-zA-Z0-9_]+):(.+)$");
  private static final java.util.Set<String> REDIS_KEYS = java.util.Set.of("connected_clients",
      "used_memory",
      "used_memory_peak",
      "uptime_in_seconds",
      "total_commands_processed",
      "instantaneous_ops_per_sec",
      "keyspace_hits",
      "keyspace_misses",
      "evicted_keys",
      "expired_keys",
      "total_connections_received",
      "rejected_connections");

  private Map<String, Object> probeRedis(
    String containerId) {
    String out = docker.execCapture(containerId, new String[]{"redis-cli", "INFO"}, EXEC_TIMEOUT_SECONDS);
    if (out == null)
      return Map.of();
    Map<String, Object> m = new LinkedHashMap<>();
    long dbKeys = 0;
    for (String line : out.split("\\R")) {
      if (line.isEmpty() || line.startsWith("#"))
        continue;
      Matcher mt = REDIS_KV.matcher(line.trim());
      if (!mt.matches())
        continue;
      String k = mt.group(1);
      String v = mt.group(2).trim();
      if (REDIS_KEYS.contains(k)) {
        putLong(m, camel(k), v);
      } else if (k.startsWith("db") && v.contains("keys=")) {
        // dbN:keys=NN,expires=NN,avg_ttl=NN
        for (String kv : v.split(",")) {
          if (kv.startsWith("keys=")) {
            try {
              dbKeys += Long.parseLong(kv.substring(5));
            } catch (NumberFormatException ignored) {
            }
          }
        }
      }
    }
    if (dbKeys > 0)
      m.put("totalKeys", dbKeys);
    return m;
  }

  // ── MongoDB ───────────────────────────────────────────────────────────
  private Map<String, Object> probeMongo(
    DeploymentConfig cfg,
    String containerId) {
    String user = nonBlank(cfg.getUsername(), "");
    String pass = nonBlank(cfg.getPassword(), "");
    // Single eval that prints "k=v" pairs, easy to parse and shell-free.
    String script = "const s=db.serverStatus();" + "print('connectionsCurrent='+s.connections.current);"
        + "print('connectionsAvailable='+s.connections.available);" + "print('opcountersInsert='+s.opcounters.insert);"
        + "print('opcountersQuery='+s.opcounters.query);" + "print('opcountersUpdate='+s.opcounters.update);"
        + "print('opcountersDelete='+s.opcounters.delete);" + "print('opcountersCommand='+s.opcounters.command);"
        + "print('serverUptimeSeconds='+Math.floor(s.uptime));" + "print('networkBytesIn='+s.network.bytesIn);"
        + "print('networkBytesOut='+s.network.bytesOut);";
    String[] cmd;
    if (!user.isEmpty() && !pass.isEmpty()) {
      cmd = new String[]{"mongosh", "--quiet", "-u", user, "-p", pass, "--authenticationDatabase", "admin", "--eval",
          script};
    } else {
      cmd = new String[]{"mongosh", "--quiet", "--eval", script};
    }
    String out = docker.execCapture(containerId, cmd, EXEC_TIMEOUT_SECONDS);
    if (out == null) {
      // Older images ship only the legacy `mongo` shell
      if (!user.isEmpty() && !pass.isEmpty()) {
        cmd = new String[]{"mongo", "--quiet", "-u", user, "-p", pass, "--authenticationDatabase", "admin", "--eval",
            script};
      } else {
        cmd = new String[]{"mongo", "--quiet", "--eval", script};
      }
      out = docker.execCapture(containerId, cmd, EXEC_TIMEOUT_SECONDS);
      if (out == null)
        return Map.of();
    }
    Map<String, Object> m = new LinkedHashMap<>();
    for (String line : out.split("\\R")) {
      int eq = line.indexOf('=');
      if (eq <= 0)
        continue;
      putLong(m,
          line.substring(0, eq).trim(),
          line.substring(eq + 1).trim());
    }
    return m;
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  private static final int EXEC_TIMEOUT_SECONDS = 3;

  private static void putLong(
    Map<String, Object> map,
    String key,
    String raw) {
    if (raw == null)
      return;
    String v = raw.trim();
    if (v.isEmpty())
      return;
    try {
      // strip thousands separators / fractional bytes
      if (v.contains(".")) {
        map.put(key, (long) Double.parseDouble(v));
      } else {
        map.put(key, Long.parseLong(v));
      }
    } catch (NumberFormatException ignored) {
      // skip non-numeric values
    }
  }

  private static String camel(
    String snake) {
    StringBuilder sb = new StringBuilder(snake.length());
    boolean up = false;
    for (int i = 0; i < snake.length(); i++) {
      char c = snake.charAt(i);
      if (c == '_') {
        up = true;
        continue;
      }
      sb.append(up ? Character.toUpperCase(c) : c);
      up = false;
    }
    return sb.toString();
  }

  private static String nonBlank(
    String s,
    String fallback) {
    return (s == null || s.isBlank()) ? fallback : s;
  }
}

package com.dbdeployer.deploy;

import com.dbdeployer.api.dto.DiscoveredContainerDto;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.InstanceStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BrewDeployEngine {

  private final OsDetector osDetector;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public BrewDeployEngine(OsDetector osDetector) {
    this.osDetector = osDetector;
  }

  public boolean isAvailable() {
    return osDetector.detectOs() == OsDetector.OsType.MACOS && osDetector.isBrewAvailable();
  }

  public List<DiscoveredContainerDto> discoverServices(
      Set<String> trackedIds, Set<String> trackedNames) {
    if (!isAvailable()) return List.of();

    List<Map<String, Object>> services = listServices();
    List<DiscoveredContainerDto> result = new ArrayList<>();

    for (Map<String, Object> svc : services) {
      String serviceName = asText(svc.get("name"));
      if (serviceName == null || serviceName.isBlank()) continue;

      DbType dbType = detectDbType(serviceName);
      if (dbType == null) continue;

      String syntheticId = toSyntheticId(serviceName);
      if (trackedIds.contains(syntheticId)) continue;
      if (trackedNames.contains(serviceName)) continue;

      var def = DatabaseCatalog.get(dbType);
      double dbVersion = 0.0;
      int port = def != null ? def.defaultPort() : 0;
      String displayName = def != null ? def.displayName() : dbType.name();
      String icon = def != null ? def.icon() : "🗄️";

      result.add(
          new DiscoveredContainerDto(
              syntheticId,
              serviceName,
              "homebrew/" + serviceName,
              dbType,
              displayName,
              Double.toString(dbVersion),
              icon,
              port > 0 ? port : null,
              port,
              toDiscoveryStatus(asText(svc.get("status")))));
    }

    return result;
  }

  public InstanceStatus getServiceStatusByContainerId(String containerId, String fallbackName) {
    String serviceName = fromSyntheticId(containerId, fallbackName);
    return getServiceStatus(serviceName);
  }

  public InstanceStatus getServiceStatus(String serviceName) {
    if (!isAvailable()) return InstanceStatus.ERROR;
    if (serviceName == null || serviceName.isBlank()) return InstanceStatus.ERROR;

    for (Map<String, Object> svc : listServices()) {
      String name = asText(svc.get("name"));
      if (serviceName.equals(name)) {
        return toInstanceStatus(asText(svc.get("status")));
      }
    }
    return InstanceStatus.ERROR;
  }

  public void startServiceByContainerId(String containerId, String fallbackName) {
    startService(fromSyntheticId(containerId, fallbackName));
  }

  public void stopServiceByContainerId(String containerId, String fallbackName) {
    stopService(fromSyntheticId(containerId, fallbackName));
  }

  public void startService(String serviceName) {
    if (!isAvailable()) {
      throw new IllegalStateException("Homebrew is not available on this machine");
    }
    if (serviceName == null || serviceName.isBlank()) {
      throw new IllegalArgumentException("Homebrew service name is required");
    }
    runCommand("brew", "services", "start", serviceName);
  }

  public void stopService(String serviceName) {
    if (!isAvailable()) {
      throw new IllegalStateException("Homebrew is not available on this machine");
    }
    if (serviceName == null || serviceName.isBlank()) {
      throw new IllegalArgumentException("Homebrew service name is required");
    }
    runCommand("brew", "services", "stop", serviceName);
  }

  public static String toSyntheticId(String serviceName) {
    return "brew:" + serviceName;
  }

  private String fromSyntheticId(String containerId, String fallbackName) {
    if (containerId != null && containerId.startsWith("brew:")) {
      return containerId.substring("brew:".length());
    }
    return fallbackName;
  }

  private List<Map<String, Object>> listServices() {
    try {
      String output = runCommand("brew", "services", "list", "--json");
      if (output == null || output.isBlank()) return List.of();
      return objectMapper.readValue(output, new TypeReference<>() {});
    } catch (Exception e) {
      log.debug("Could not list Homebrew services: {}", e.getMessage());
      return List.of();
    }
  }

  private DbType detectDbType(String serviceName) {
    if (serviceName == null || serviceName.isBlank()) {
      return null;
    }

    String lower = serviceName.toLowerCase();

    return switch (lower) {
      case String s when s.contains("postgres") || s.contains("postgresql") -> DbType.POSTGRESQL;

      case String s when s.contains("pgadmin") -> DbType.PGADMIN;

      case String s when s.contains("mysql") -> DbType.MYSQL;

      case String s when s.contains("mariadb") -> DbType.MARIADB;

      case String s
          when s.contains("mssql")
              || s.contains("sqlserver")
              || s.contains("sql-server")
              || s.contains("microsoft-sql-server") ->
          DbType.MSSQL;

      case String s when s.contains("h2") -> DbType.H2;

      case String s when s.contains("mongo") || s.contains("mongodb") -> DbType.MONGODB;

      case String s when s.contains("couchdb") -> DbType.COUCHDB;

      case String s when s.contains("neo4j") -> DbType.NEO4J;

      case String s when s.contains("dynamodb") || s.contains("dynamodb-local") ->
          DbType.DYNAMODB_LOCAL;

      case String s when s.contains("redis") -> DbType.REDIS;

      case String s when s.contains("cassandra") -> DbType.CASSANDRA;

      case String s when s.contains("clickhouse") -> DbType.CLICKHOUSE;

      case String s when s.contains("elasticsearch") || s.contains("elastic-search") ->
          DbType.ELASTICSEARCH;

      case String s when s.contains("rabbitmq") || s.contains("rabbit-mq") -> DbType.RABBITMQ;

      case String s when s.contains("kafka") -> DbType.KAFKA;

      case String s when s.contains("conduktor") -> DbType.CONDUKTOR;

      case String s when s.contains("grafana") -> DbType.GRAFANA;

      case String s when s.contains("prometheus") -> DbType.PROMETHEUS;

      case String s when s.contains("loki") -> DbType.LOKI;

      case String s when s.contains("minio") || s.contains("minio-server") -> DbType.MINIO;

      case String s when s.contains("keycloak") -> DbType.KEYCLOAK;

      case String s when s.contains("vault") || s.contains("hashicorp-vault") -> DbType.VAULT;

      case String s when s.contains("nginx") -> DbType.NGINX;

      case String s when s.contains("adminer") -> DbType.ADMINER;

      default -> null;
    };
  }

  private static String toDiscoveryStatus(String brewStatus) {
    return switch (toInstanceStatus(brewStatus)) {
      case RUNNING -> "RUNNING";
      case ERROR -> "ERROR";
      default -> "STOPPED";
    };
  }

  private static InstanceStatus toInstanceStatus(String brewStatus) {
    if (brewStatus == null) return InstanceStatus.STOPPED;
    String s = brewStatus.trim().toLowerCase();
    if ("started".equals(s)) return InstanceStatus.RUNNING;
    if ("error".equals(s)) return InstanceStatus.ERROR;
    return InstanceStatus.STOPPED;
  }

  private static String asText(Object o) {
    return o == null ? null : String.valueOf(o);
  }

  private String runCommand(String... command) {
    Process process = null;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();

      boolean finished = process.waitFor(20, TimeUnit.SECONDS);
      String output;
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        output = reader.lines().reduce("", (a, b) -> a + (a.isEmpty() ? "" : "\n") + b);
      }

      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("Command timed out: " + String.join(" ", command));
      }

      if (process.exitValue() != 0) {
        throw new IllegalStateException(
            "Command failed: " + String.join(" ", command) + "\n" + output);
      }
      return output;
    } catch (IOException e) {
      throw new IllegalStateException("Could not execute command: " + String.join(" ", command), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Command interrupted: " + String.join(" ", command), e);
    }
  }
}

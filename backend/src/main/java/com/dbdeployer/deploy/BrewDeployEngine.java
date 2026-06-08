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

    public List<DiscoveredContainerDto> discoverServices(Set<String> trackedIds, Set<String> trackedNames) {
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
            int port = def != null ? def.defaultPort() : 0;
            String displayName = def != null ? def.displayName() : dbType.name();
            String icon = def != null ? def.icon() : "🗄️";

            result.add(new DiscoveredContainerDto(
                    syntheticId,
                    serviceName,
                    "homebrew/" + serviceName,
                    dbType,
                    displayName,
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
            return objectMapper.readValue(output, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.debug("Could not list Homebrew services: {}", e.getMessage());
            return List.of();
        }
    }

    private DbType detectDbType(String serviceName) {
        String lower = serviceName.toLowerCase();

        if (lower.contains("postgres")) return DbType.POSTGRESQL;
        if (lower.contains("kafka")) return DbType.KAFKA;
        if (lower.contains("redis")) return DbType.REDIS;
        if (lower.contains("mysql")) return DbType.MYSQL;
        if (lower.contains("mariadb")) return DbType.MARIADB;
        if (lower.contains("mongodb")) return DbType.MONGODB;
        if (lower.contains("cassandra")) return DbType.CASSANDRA;
        if (lower.contains("elasticsearch")) return DbType.ELASTICSEARCH;
        if (lower.contains("clickhouse")) return DbType.CLICKHOUSE;
        if (lower.contains("neo4j")) return DbType.NEO4J;
        if (lower.contains("couchdb")) return DbType.COUCHDB;

        return null;
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
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (a, b) -> a + (a.isEmpty() ? "" : "\n") + b);
            }

            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Command timed out: " + String.join(" ", command));
            }

            if (process.exitValue() != 0) {
                throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + output);
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

package com.dbdeployer.deploy;

import com.dbdeployer.api.dto.DiscoveredContainerDto;
import com.dbdeployer.model.DbInstance;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.InstanceStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.dbdeployer.config.DockerSocketResolver;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class DockerDeployEngine {

    private static final Logger log = LoggerFactory.getLogger(DockerDeployEngine.class);
    private final DockerClient docker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // DB image name → DbType mapping (order matters: more specific first)
    private static final Map<String, DbType> IMAGE_DB_TYPE_MAP = new LinkedHashMap<>();
    static {
        IMAGE_DB_TYPE_MAP.put("mariadb",        DbType.MARIADB);
        IMAGE_DB_TYPE_MAP.put("postgres",        DbType.POSTGRESQL);
        IMAGE_DB_TYPE_MAP.put("mysql",           DbType.MYSQL);
        IMAGE_DB_TYPE_MAP.put("mongo",           DbType.MONGODB);
        IMAGE_DB_TYPE_MAP.put("redis",           DbType.REDIS);
        IMAGE_DB_TYPE_MAP.put("cassandra",       DbType.CASSANDRA);
        IMAGE_DB_TYPE_MAP.put("couchdb",         DbType.COUCHDB);
        IMAGE_DB_TYPE_MAP.put("clickhouse",      DbType.CLICKHOUSE);
        IMAGE_DB_TYPE_MAP.put("neo4j",           DbType.NEO4J);
        IMAGE_DB_TYPE_MAP.put("elasticsearch",   DbType.ELASTICSEARCH);
        IMAGE_DB_TYPE_MAP.put("mssql",           DbType.MSSQL);
        IMAGE_DB_TYPE_MAP.put("sqlserver",       DbType.MSSQL);
        IMAGE_DB_TYPE_MAP.put("dynamodb-local",  DbType.DYNAMODB_LOCAL);
    }

    public DockerDeployEngine() {
        String socketUri = DockerSocketResolver.resolve();
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(socketUri)
                .build();
        var httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        this.docker = DockerClientImpl.getInstance(config, httpClient);
    }

    /** Check Docker is reachable on this machine */
    public boolean isDockerAvailable() {
        try {
            docker.pingCmd().exec();
            return true;
        } catch (Exception e) {
            log.warn("Docker not available: {}", e.getMessage());
            return false;
        }
    }

    /** Pull image + create + start container, return updated instance with containerId + startedAt */
    public DbInstance deploy(DbInstance instance) throws Exception {
        var def = DatabaseCatalog.get(instance.getDbType());
        String image = def.dockerImage() + ":" + instance.getVersion();
        String containerName = "dbdeployer-" + instance.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");

        instance.setContainerName(containerName);
        instance.setDeployMethod(DeployMethod.DOCKER);

        // Pull image
        log.info("Pulling image: {}", image);
        docker.pullImageCmd(image)
                .start()
                .awaitCompletion();

        // Build env vars
        List<String> envVars = buildEnvVars(instance, def);

        // Port binding
        ExposedPort containerPort = ExposedPort.tcp(instance.getContainerPort());
        Ports portBindings = new Ports();
        portBindings.bind(containerPort, Ports.Binding.bindPort(instance.getHostPort()));

        // Volume binding for data persistence
        List<Bind> binds = new ArrayList<>();
        if (def.dataVolumePath() != null) {
            Path hostDataDir = Paths.get(System.getProperty("user.home"),
                    ".db-deployer", "data", instance.getId());
            Files.createDirectories(hostDataDir);
            binds.add(new Bind(hostDataDir.toAbsolutePath().toString(),
                    new Volume(def.dataVolumePath())));
            instance.setDataDirectory(hostDataDir.toAbsolutePath().toString());
        }

        // Extra exposed port for Neo4j bolt
        List<ExposedPort> exposedPorts = new ArrayList<>();
        exposedPorts.add(containerPort);
        if (instance.getDbType() == DbType.NEO4J) {
            ExposedPort boltPort = ExposedPort.tcp(7687);
            exposedPorts.add(boltPort);
            portBindings.bind(boltPort, Ports.Binding.bindPort(7687));
        }
        // ClickHouse HTTP port
        if (instance.getDbType() == DbType.CLICKHOUSE) {
            ExposedPort httpPort = ExposedPort.tcp(8123);
            exposedPorts.add(httpPort);
            portBindings.bind(httpPort, Ports.Binding.bindPort(8123));
        }

        // Create container
        CreateContainerResponse container = docker.createContainerCmd(image)
                .withName(containerName)
                .withEnv(envVars)
                .withExposedPorts(exposedPorts)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withBinds(binds)
                        .withRestartPolicy(RestartPolicy.unlessStoppedRestart()))
                .exec();

        instance.setContainerId(container.getId());

        // Start container
        docker.startContainerCmd(container.getId()).exec();
        instance.setStatus(InstanceStatus.RUNNING);
        instance.setStartedAt(getStartedAt(container.getId()));

        log.info("Container started: {} ({})", containerName, container.getId());
        return instance;
    }

    public void start(DbInstance instance) {
        docker.startContainerCmd(instance.getContainerId()).exec();
    }

    public void stop(DbInstance instance) {
        docker.stopContainerCmd(instance.getContainerId()).withTimeout(15).exec();
    }

    public void remove(DbInstance instance) {
        try {
            docker.stopContainerCmd(instance.getContainerId()).withTimeout(5).exec();
        } catch (Exception ignored) {}
        docker.removeContainerCmd(instance.getContainerId()).withForce(true).exec();
    }

    public InstanceStatus getStatus(DbInstance instance) {
        try {
            InspectContainerResponse info = docker.inspectContainerCmd(instance.getContainerId()).exec();
            InspectContainerResponse.ContainerState state = info.getState();
            if (Boolean.TRUE.equals(state.getRunning()))  return InstanceStatus.RUNNING;
            if (Boolean.TRUE.equals(state.getPaused()))   return InstanceStatus.STOPPED;
            return InstanceStatus.STOPPED;
        } catch (NotFoundException e) {
            return InstanceStatus.ERROR;
        }
    }

    /** Returns the container ID for a named container, or null if not found. */
    public String getContainerId(String containerName) {
        try {
            return docker.inspectContainerCmd(containerName).exec().getId();
        } catch (NotFoundException e) {
            return null;
        }
    }

    /**
     * Returns the UTC time the container was last started, parsed from Docker's
     * ISO-8601 "StartedAt" field. Returns null if the container hasn't started
     * or the timestamp is the Docker zero value (0001-01-01).
     */
    public LocalDateTime getStartedAt(String containerId) {
        try {
            String raw = docker.inspectContainerCmd(containerId).exec()
                    .getState().getStartedAt();
            if (raw == null || raw.startsWith("0001")) return null;
            return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME)
                    .toLocalDateTime();
        } catch (Exception e) {
            log.debug("Could not read startedAt for container {}: {}", containerId, e.getMessage());
            return null;
        }
    }

    /**
     * Lists all running Docker containers whose images look like a database engine
     * and whose IDs/names are not already tracked by DB Deployer.
     */
    public List<DiscoveredContainerDto> discoverContainers(Set<String> trackedIds,
                                                           Set<String> trackedNames) {
        List<Container> running;
        try {
            running = docker.listContainersCmd().withShowAll(false).exec();
        } catch (Exception e) {
            log.warn("Could not list Docker containers for discovery: {}", e.getMessage());
            return List.of();
        }

        List<DiscoveredContainerDto> result = new ArrayList<>();

        for (Container c : running) {
            // Strip the leading "/" Docker adds to names
            String name = c.getNames() != null && c.getNames().length > 0
                    ? c.getNames()[0].replaceFirst("^/", "")
                    : c.getId().substring(0, 12);

            // Skip already-tracked containers
            if (trackedIds.contains(c.getId())) continue;
            if (trackedNames.contains(name))    continue;

            // Detect DB type from image
            String image = c.getImage();
            DbType dbType = detectDbType(image);
            if (dbType == null) continue; // not a recognised DB image

            // Pick the first public (host) port mapping
            Integer hostPort = null;
            int containerPort = 0;
            ContainerPort[] ports = c.getPorts();
            if (ports != null) {
                for (ContainerPort p : ports) {
                    if (p.getPublicPort() != null && p.getPublicPort() > 0) {
                        hostPort      = p.getPublicPort();
                        containerPort = p.getPrivatePort();
                        break;
                    }
                }
            }
            // Fallback: use catalog default port
            if (containerPort == 0) {
                var def = DatabaseCatalog.get(dbType);
                if (def != null) containerPort = def.defaultPort();
            }

            var def = DatabaseCatalog.get(dbType);
            String displayName = def != null ? def.displayName() : dbType.name();
            String icon        = def != null ? def.icon()        : "🗄️";

            result.add(new DiscoveredContainerDto(
                    c.getId(),
                    name,
                    image,
                    dbType,
                    displayName,
                    icon,
                    hostPort,
                    containerPort,
                    c.getState()
            ));
        }

        return result;
    }

    /** Fetch last N lines of container logs */
    public String getLogs(DbInstance instance, int tail) throws InterruptedException {
        StringBuilder sb = new StringBuilder();
        docker.logContainerCmd(instance.getContainerId())
                .withStdOut(true)
                .withStdErr(true)
                .withTail(tail)
                .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(Frame item) {
                        sb.append(new String(item.getPayload()));
                    }
                })
                .awaitCompletion();
        return sb.toString();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private DbType detectDbType(String image) {
        if (image == null) return null;
        String lower = image.toLowerCase();
        for (Map.Entry<String, DbType> entry : IMAGE_DB_TYPE_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private List<String> buildEnvVars(DbInstance instance, DatabaseCatalog.DbDefinition def) throws IOException {
        List<String> env = new ArrayList<>();

        // Extra custom env vars stored as JSON
        Map<String, String> extra = new LinkedHashMap<>();
        if (instance.getExtraEnvJson() != null && !instance.getExtraEnvJson().isBlank()) {
            extra = objectMapper.readValue(instance.getExtraEnvJson(),
                    new TypeReference<Map<String, String>>() {});
        }

        for (var ev : def.credentialEnvVars()) {
            String value = switch (ev.type()) {
                case TEXT     -> extra.getOrDefault(ev.name(), ev.placeholder());
                case PASSWORD -> switch (ev.name()) {
                    case "POSTGRES_PASSWORD", "MYSQL_PASSWORD", "MARIADB_PASSWORD",
                         "MONGO_INITDB_ROOT_PASSWORD", "REDIS_PASSWORD",
                         "CASSANDRA_PASSWORD", "COUCHDB_PASSWORD",
                         "CLICKHOUSE_PASSWORD", "ELASTIC_PASSWORD" -> instance.getPassword() != null && !instance.getPassword().isBlank()
                            ? instance.getPassword() : ev.placeholder();
                    case "MYSQL_ROOT_PASSWORD", "MARIADB_ROOT_PASSWORD", "SA_PASSWORD" ->
                            extra.getOrDefault(ev.name(), ev.placeholder());
                    default -> extra.getOrDefault(ev.name(), ev.placeholder());
                };
                case DATABASE -> instance.getDatabaseName() != null && !instance.getDatabaseName().isBlank()
                        ? instance.getDatabaseName() : ev.placeholder();
            };

            // Username handling — covers both standard USER vars and DB-specific root username vars
            if (ev.type() == DatabaseCatalog.EnvVarType.TEXT) {
                boolean isUsernameVar = ev.name().equals("MONGO_INITDB_ROOT_USERNAME")
                        || ev.name().equals("COUCHDB_USER")
                        || ev.name().equals("CLICKHOUSE_USER")
                        || ev.name().equals("ELASTIC_USERNAME")
                        || (ev.name().contains("USER") && !ev.name().contains("ROOT"));
                if (isUsernameVar && instance.getUsername() != null && !instance.getUsername().isBlank()) {
                    value = instance.getUsername();
                }
            }

            env.add(ev.name() + "=" + value);
        }

        return env;
    }
}

package com.dbdeployer.deploy;

import com.dbdeployer.api.dto.ContainerMetricsResponse;
import com.dbdeployer.api.dto.DiscoveredContainerDto;
import com.dbdeployer.config.DockerSocketResolver;
import com.dbdeployer.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DockerDeployEngine {

  private final DockerClient docker;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, ReentrantLock> imagePullLocks = new ConcurrentHashMap<>();

  // Container image name → DbType mapping (order matters: more specific first)
  private static final Map<String, DbType> IMAGE_DB_TYPE_MAP = new LinkedHashMap<>();

  static {
    // ── Relational ────────────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("mariadb", DbType.MARIADB);
    IMAGE_DB_TYPE_MAP.put("postgres", DbType.POSTGRESQL);
    IMAGE_DB_TYPE_MAP.put("mysql", DbType.MYSQL);
    IMAGE_DB_TYPE_MAP.put("mssql", DbType.MSSQL);
    IMAGE_DB_TYPE_MAP.put("sqlserver", DbType.MSSQL);
    // ── NoSQL ─────────────────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("mongo", DbType.MONGODB);
    IMAGE_DB_TYPE_MAP.put("couchdb", DbType.COUCHDB);
    IMAGE_DB_TYPE_MAP.put("neo4j", DbType.NEO4J);
    IMAGE_DB_TYPE_MAP.put("dynamodb-local", DbType.DYNAMODB_LOCAL);
    // ── Cache / KV ────────────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("redis", DbType.REDIS);
    // ── Wide-column / OLAP ────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("cassandra", DbType.CASSANDRA);
    IMAGE_DB_TYPE_MAP.put("clickhouse", DbType.CLICKHOUSE);
    // ── Search ────────────────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("elasticsearch", DbType.ELASTICSEARCH);
    // ── Messaging ─────────────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("rabbitmq", DbType.RABBITMQ);
    IMAGE_DB_TYPE_MAP.put("apache/kafka", DbType.KAFKA);
    IMAGE_DB_TYPE_MAP.put("kafka", DbType.KAFKA);
    // ── Messaging UIs ─────────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("conduktor/conduktor-console", DbType.CONDUKTOR);
    IMAGE_DB_TYPE_MAP.put("conduktor-console", DbType.CONDUKTOR);
    // ── Observability ─────────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("grafana/grafana", DbType.GRAFANA);
    IMAGE_DB_TYPE_MAP.put("grafana/loki", DbType.LOKI);
    IMAGE_DB_TYPE_MAP.put("grafana", DbType.GRAFANA); // fallback: any image containing "grafana"
    IMAGE_DB_TYPE_MAP.put("prom/prometheus", DbType.PROMETHEUS);
    IMAGE_DB_TYPE_MAP.put("prometheus", DbType.PROMETHEUS);
    // ── Object storage ────────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("minio/minio", DbType.MINIO);
    IMAGE_DB_TYPE_MAP.put("minio", DbType.MINIO);
    // ── Identity / secrets ────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("keycloak/keycloak", DbType.KEYCLOAK);
    IMAGE_DB_TYPE_MAP.put("keycloak", DbType.KEYCLOAK);
    IMAGE_DB_TYPE_MAP.put("hashicorp/vault", DbType.VAULT);
    IMAGE_DB_TYPE_MAP.put("vault", DbType.VAULT);
    // ── Web / proxy ───────────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("nginx", DbType.NGINX);
    // ── DB admin UIs ──────────────────────────────────────────────────────
    IMAGE_DB_TYPE_MAP.put("adminer", DbType.ADMINER);
    IMAGE_DB_TYPE_MAP.put("pgadmin4", DbType.PGADMIN);
    IMAGE_DB_TYPE_MAP.put("pgadmin", DbType.PGADMIN);
  }

  public DockerDeployEngine() {
    String socketUri = DockerSocketResolver.resolve();
    var config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(socketUri).build();
    var httpClient = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost())
        .sslConfig(config.getSSLConfig()).build();
    this.docker = DockerClientImpl.getInstance(config, httpClient);
  }

  // ── Granular step methods (used by pipeline step impls) ───────────────────

  /**
   * Pull the named Docker image (e.g. {@code postgres:16}). Blocks until the pull
   * is complete.
   */
  public void pullImage(String image) throws Exception {
    log.info("[docker] Pulling image {}", image);
    docker.pullImageCmd(image).start().awaitCompletion();
    log.info("[docker] Pull complete for {}", image);
  }

  /**
   * Ensures an image is available locally and avoids duplicate concurrent pulls
   * for the same image:tag combination.
   *
   * @return true if a pull was executed, false if the image was already local
   */
  public boolean ensureImageAvailable(String imageName, String tag) throws Exception {
    String normalizedImage = imageName == null ? "" : imageName.trim().toLowerCase(Locale.ROOT);
    String normalizedTag = tag == null || tag.isBlank() ? "latest" : tag.trim().toLowerCase(Locale.ROOT);
    String lockKey = normalizedImage + ":" + normalizedTag;

    ReentrantLock lock = imagePullLocks.computeIfAbsent(lockKey, ignored -> new ReentrantLock(true));
    lock.lock();
    try {
      if (isImageAvailableLocally(normalizedImage, normalizedTag)) {
        log.info("[docker] Reusing local image {}:{}", normalizedImage, normalizedTag);
        return false;
      }

      log.info("[docker] Local image missing, pull required for {}:{}", normalizedImage, normalizedTag);
      pullImage(normalizedImage + ":" + normalizedTag);
      return true;
    } finally {
      lock.unlock();
      if (!lock.hasQueuedThreads()) {
        imagePullLocks.remove(lockKey, lock);
      }
    }
  }

  /**
   * Create (but do not start) a Docker container for the given config. Populates
   * {@code
   * container.containerId}, {@code containerName}, and {@code dataDirectory}
   * in-place. The caller must persist.
   */
  public void createContainer(DeploymentConfig config, DeployedContainer container) throws Exception {
    var def = DatabaseCatalog.get(config.getDbType());
    String image = def.dockerImage() + ":" + config.getVersion();
    String containerName = "dbdeployer-" + config.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");

    log.info("[docker] Creating container '{}' from image {} on hostPort={} containerPort={}", containerName, image,
        config.getHostPort(), config.getContainerPort());

    container.setContainerName(containerName);

    List<String> envVars = buildEnvVars(config, def);

    ExposedPort exposed = ExposedPort.tcp(config.getContainerPort());
    Ports portBindings = new Ports();
    portBindings.bind(exposed, Ports.Binding.bindPort(config.getHostPort()));

    List<Bind> binds = new ArrayList<>();
    if (def.dataVolumePath() != null) {
      Path hostDataDir = Paths.get(System.getProperty("user.home"), ".db-deployer", "data", config.getId());
      Files.createDirectories(hostDataDir);
      binds.add(new Bind(hostDataDir.toAbsolutePath().toString(), new Volume(def.dataVolumePath())));
      container.setDataDirectory(hostDataDir.toAbsolutePath().toString());
    }

    List<ExposedPort> exposedPorts = new ArrayList<>();
    exposedPorts.add(exposed);
    if (config.getDbType() == DbType.NEO4J) {
      ExposedPort bolt = ExposedPort.tcp(7687);
      exposedPorts.add(bolt);
      portBindings.bind(bolt, Ports.Binding.bindPort(7687));
    }
    if (config.getDbType() == DbType.CLICKHOUSE) {
      ExposedPort http = ExposedPort.tcp(8123);
      exposedPorts.add(http);
      portBindings.bind(http, Ports.Binding.bindPort(8123));
    }
    if (config.getDbType() == DbType.KAFKA) {
      ExposedPort controller = ExposedPort.tcp(9093);
      exposedPorts.add(controller);
      portBindings.bind(controller, Ports.Binding.bindPort(9093));
    }

    CreateContainerResponse created = docker.createContainerCmd(image).withName(containerName).withEnv(envVars)
        .withExposedPorts(exposedPorts).withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings)
            .withBinds(binds).withRestartPolicy(RestartPolicy.unlessStoppedRestart()))
        .exec();

    container.setContainerId(created.getId());
    log.info("[docker] Container created: {} ({})", containerName, created.getId());
  }

  /**
   * Start a previously-created container. Does NOT set {@code container.status}
   * or {@code
   * startedAt} — the caller or {@code FinaliseStep} is responsible for those.
   */
  public void startContainer(DeployedContainer container) {
    log.info("[docker] Starting container {} ({})", container.getContainerName(), container.getContainerId());
    docker.startContainerCmd(container.getContainerId()).exec();
    log.info("[docker] Start command sent for container {} ({})", container.getContainerName(),
        container.getContainerId());
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

  /**
   * Returns a normalized set of local image refs currently present in Docker,
   * e.g. {@code
   * postgres:16}, {@code docker.io/library/postgres:16}.
   */
  public Set<String> getLocalImageReferences() {
    Set<String> refs = new HashSet<>();
    try {
      List<Image> images = docker.listImagesCmd().withShowAll(true).exec();
      for (Image image : images) {
        if (image.getRepoTags() == null)
          continue;
        for (String tag : image.getRepoTags()) {
          if (tag == null || tag.isBlank() || "<none>:<none>".equals(tag))
            continue;
          refs.add(tag.trim().toLowerCase(Locale.ROOT));
        }
      }
    } catch (Exception e) {
      log.warn("Could not list local Docker images: {}", e.getMessage());
    }
    return refs;
  }

  /** Fast check using an existing local refs snapshot. */
  public boolean hasLocalImage(String image, String tag, Set<String> localRefs) {
    if (image == null || image.isBlank() || tag == null || tag.isBlank())
      return false;
    if (localRefs == null || localRefs.isEmpty())
      return false;

    String normalizedImage = image.trim().toLowerCase(Locale.ROOT);
    String normalizedTag = tag.trim().toLowerCase(Locale.ROOT);
    Set<String> candidates = localRefCandidates(normalizedImage, normalizedTag);
    for (String candidate : candidates) {
      if (localRefs.contains(candidate))
        return true;
    }
    return false;
  }

  /** Live local image existence check for one image:tag pair. */
  public boolean isImageAvailableLocally(String image, String tag) {
    return hasLocalImage(image, tag, getLocalImageReferences());
  }

  /**
   * Pull image → create → start container. Populates
   * {@code container.containerId}, {@code
   * containerName}, {@code dataDirectory}, {@code startedAt} in-place. The caller
   * is responsible for persisting the container.
   */
  public void deploy(DeploymentConfig config, DeployedContainer container) throws Exception {
    var def = DatabaseCatalog.get(config.getDbType());
    String image = def.dockerImage() + ":" + config.getVersion();
    String containerName = "dbdeployer-" + config.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");

    container.setContainerName(containerName);

    // Pull image
    log.info("Pulling image: {}", image);
    docker.pullImageCmd(image).start().awaitCompletion();

    // Build env vars
    List<String> envVars = buildEnvVars(config, def);

    // Port binding
    ExposedPort exposed = ExposedPort.tcp(config.getContainerPort());
    Ports portBindings = new Ports();
    portBindings.bind(exposed, Ports.Binding.bindPort(config.getHostPort()));

    // Volume binding for data persistence
    List<Bind> binds = new ArrayList<>();
    if (def.dataVolumePath() != null) {
      Path hostDataDir = Paths.get(System.getProperty("user.home"), ".db-deployer", "data", config.getId());
      Files.createDirectories(hostDataDir);
      binds.add(new Bind(hostDataDir.toAbsolutePath().toString(), new Volume(def.dataVolumePath())));
      container.setDataDirectory(hostDataDir.toAbsolutePath().toString());
    }

    // Extra exposed ports (Neo4j bolt, ClickHouse HTTP, Kafka controller)
    List<ExposedPort> exposedPorts = new ArrayList<>();
    exposedPorts.add(exposed);
    if (config.getDbType() == DbType.NEO4J) {
      ExposedPort bolt = ExposedPort.tcp(7687);
      exposedPorts.add(bolt);
      portBindings.bind(bolt, Ports.Binding.bindPort(7687));
    }
    if (config.getDbType() == DbType.CLICKHOUSE) {
      ExposedPort http = ExposedPort.tcp(8123);
      exposedPorts.add(http);
      portBindings.bind(http, Ports.Binding.bindPort(8123));
    }
    if (config.getDbType() == DbType.KAFKA) {
      ExposedPort controller = ExposedPort.tcp(9093);
      exposedPorts.add(controller);
      portBindings.bind(controller, Ports.Binding.bindPort(9093));
    }

    // Create container
    CreateContainerResponse created = docker.createContainerCmd(image).withName(containerName).withEnv(envVars)
        .withExposedPorts(exposedPorts).withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings)
            .withBinds(binds).withRestartPolicy(RestartPolicy.unlessStoppedRestart()))
        .exec();

    container.setContainerId(created.getId());

    // Start container
    docker.startContainerCmd(created.getId()).exec();
    container.setStatus(InstanceStatus.RUNNING);
    container.setStartedAt(getStartedAt(created.getId()));

    log.info("Container started: {} ({})", containerName, created.getId());
  }

  public void start(DeployedContainer container) {
    docker.startContainerCmd(container.getContainerId()).exec();
  }

  public void stop(DeployedContainer container) {
    docker.stopContainerCmd(container.getContainerId()).withTimeout(15).exec();
  }

  public void remove(DeployedContainer container) {
    try {
      docker.stopContainerCmd(container.getContainerId()).withTimeout(5).exec();
    } catch (Exception ignored) {
    }
    docker.removeContainerCmd(container.getContainerId()).withForce(true).exec();
  }

  public InstanceStatus getStatus(DeployedContainer container) {
    if (container.getContainerId() == null)
      return InstanceStatus.ERROR;
    try {
      InspectContainerResponse info = docker.inspectContainerCmd(container.getContainerId()).exec();
      InspectContainerResponse.ContainerState state = info.getState();
      if (Boolean.TRUE.equals(state.getRestarting()))
        return InstanceStatus.RESTARTING;
      if (Boolean.TRUE.equals(state.getRunning()))
        return InstanceStatus.RUNNING;
      if (Boolean.TRUE.equals(state.getPaused()))
        return InstanceStatus.STOPPED;
      // Crashed / OOM-killed containers have a non-zero exit code
      if (Boolean.TRUE.equals(state.getOOMKilled()))
        return InstanceStatus.ERROR;
      Long exitCode = state.getExitCodeLong();
      if (exitCode != null && exitCode != 0)
        return InstanceStatus.ERROR;
      return InstanceStatus.STOPPED;
    } catch (NotFoundException e) {
      return InstanceStatus.ERROR;
    }
  }

  /** Fetch last N lines of container logs */
  public String getLogs(DeployedContainer container, int tail) throws InterruptedException {
    StringBuilder sb = new StringBuilder();
    docker.logContainerCmd(container.getContainerId()).withStdOut(true).withStdErr(true).withTail(tail)
        .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<>() {
          @Override
          public void onNext(Frame item) {
            sb.append(new String(item.getPayload()));
          }
        }).awaitCompletion();
    return sb.toString();
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
   * Returns the UTC time the container was last started, or null if the container
   * hasn't started or the timestamp is the Docker zero value (0001-01-01).
   */
  public Instant getStartedAt(String containerId) {
    try {
      String raw = docker.inspectContainerCmd(containerId).exec().getState().getStartedAt();
      if (raw == null || raw.startsWith("0001"))
        return null;
      return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME).toInstant();
    } catch (Exception e) {
      log.debug("Could not read startedAt for container {}: {}", containerId, e.getMessage());
      return null;
    }
  }

  /**
   * Lists all running Docker containers whose images look like a database engine
   * and whose IDs / names are not already tracked by DB Deployer.
   */
  public List<DiscoveredContainerDto> discoverContainers(Set<String> trackedIds, Set<String> trackedNames) {
    List<Container> running;
    try {
      running = docker.listContainersCmd().withShowAll(true).exec();
    } catch (Exception e) {
      log.warn("Could not list Docker containers for discovery: {}", e.getMessage());
      return List.of();
    }

    List<DiscoveredContainerDto> result = new ArrayList<>();

    for (Container c : running) {
      String name = c.getNames() != null && c.getNames().length > 0
          ? c.getNames()[0].replaceFirst("^/", "")
          : c.getId().substring(0, 12);

      if (trackedIds.contains(c.getId()))
        continue;
      if (trackedNames.contains(name))
        continue;

      DbType dbType = detectDbType(c.getImage());
      if (dbType == null)
        continue;

      Integer hostPort = null;
      int containerPort = 0;
      ContainerPort[] ports = c.getPorts();
      if (ports != null) {
        for (ContainerPort p : ports) {
          if (p.getPublicPort() != null && p.getPublicPort() > 0) {
            hostPort = p.getPublicPort();
            containerPort = p.getPrivatePort();
            break;
          }
        }
      }
      if (containerPort == 0) {
        var def = DatabaseCatalog.get(dbType);
        if (def != null)
          containerPort = def.defaultPort();
      }

      var def = DatabaseCatalog.get(dbType);
      String displayName = def != null ? def.displayName() : dbType.name();
      String icon = def != null ? def.icon() : "🗄️";

      result.add(new DiscoveredContainerDto(c.getId(), name, c.getImage(), dbType, displayName, icon, hostPort,
          containerPort, c.getState()));
    }

    return result;
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  private DbType detectDbType(String image) {
    if (image == null)
      return null;
    String lower = image.toLowerCase();
    for (Map.Entry<String, DbType> entry : IMAGE_DB_TYPE_MAP.entrySet()) {
      if (lower.contains(entry.getKey()))
        return entry.getValue();
    }
    return null;
  }

  private Set<String> localRefCandidates(String image, String tag) {
    Set<String> refs = new HashSet<>();
    refs.add(image + ":" + tag);

    String trimmed = image;
    if (trimmed.startsWith("docker.io/")) {
      trimmed = trimmed.substring("docker.io/".length());
    } else if (trimmed.startsWith("index.docker.io/")) {
      trimmed = trimmed.substring("index.docker.io/".length());
    }

    refs.add(trimmed + ":" + tag);

    String[] parts = trimmed.split("/");
    boolean hasRegistryHost = parts.length > 0
        && (parts[0].contains(".") || parts[0].contains(":") || "localhost".equals(parts[0]));

    if (!hasRegistryHost) {
      if (parts.length == 1) {
        refs.add("library/" + parts[0] + ":" + tag);
        refs.add("docker.io/library/" + parts[0] + ":" + tag);
        refs.add("index.docker.io/library/" + parts[0] + ":" + tag);
      } else {
        refs.add("docker.io/" + trimmed + ":" + tag);
        refs.add("index.docker.io/" + trimmed + ":" + tag);
      }
    }

    return refs;
  }

  private List<String> buildEnvVars(DeploymentConfig config, DatabaseCatalog.DbDefinition def) throws IOException {
    List<String> env = new ArrayList<>();

    Map<String, String> extra = new LinkedHashMap<>();
    if (config.getExtraEnvJson() != null && !config.getExtraEnvJson().isBlank()) {
      extra = objectMapper.readValue(config.getExtraEnvJson(), new TypeReference<Map<String, String>>() {
      });
    }

    for (var ev : def.credentialEnvVars()) {
      String value = switch (ev.type()) {
        case TEXT -> extra.getOrDefault(ev.name(), ev.placeholder());
        case PASSWORD -> switch (ev.name()) {
          case "POSTGRES_PASSWORD", "MYSQL_PASSWORD", "MARIADB_PASSWORD", "MONGO_INITDB_ROOT_PASSWORD",
              "REDIS_PASSWORD", "CASSANDRA_PASSWORD", "COUCHDB_PASSWORD", "CLICKHOUSE_PASSWORD", "ELASTIC_PASSWORD" ->
            config.getPassword() != null && !config.getPassword().isBlank() ? config.getPassword() : ev.placeholder();
          case "MYSQL_ROOT_PASSWORD", "MARIADB_ROOT_PASSWORD", "SA_PASSWORD" ->
            extra.getOrDefault(ev.name(), ev.placeholder());
          default -> extra.getOrDefault(ev.name(), ev.placeholder());
        };
        case DATABASE -> config.getDatabaseName() != null && !config.getDatabaseName().isBlank()
            ? config.getDatabaseName()
            : ev.placeholder();
      };

      if (ev.type() == DatabaseCatalog.EnvVarType.TEXT) {
        boolean isUsernameVar = ev.name().equals("MONGO_INITDB_ROOT_USERNAME") || ev.name().equals("COUCHDB_USER")
            || ev.name().equals("CLICKHOUSE_USER") || ev.name().equals("ELASTIC_USERNAME")
            || (ev.name().contains("USER") && !ev.name().contains("ROOT"));
        if (isUsernameVar && config.getUsername() != null && !config.getUsername().isBlank()) {
          value = config.getUsername();
        }
      }

      // Substitute {port} placeholder — used by KAFKA_ADVERTISED_LISTENERS
      value = value.replace("{port}", String.valueOf(config.getHostPort()));

      env.add(ev.name() + "=" + value);
    }

    return env;
  }

  // ── Container metrics ──────────────────────────────────────────────────────

  /**
   * Collects a single-shot live metrics snapshot for a running container. Returns
   * {@link ContainerMetricsResponse#unavailable()} if the container is stopped,
   * not found, or Docker is unreachable.
   */
  public ContainerMetricsResponse getContainerMetrics(String containerId, int hostPort) {
    if (containerId == null)
      return ContainerMetricsResponse.unavailable();

    // ── 1. Docker stats (no-stream = single sample) ──────────────────────
    var statsRef = new AtomicReference<Statistics>();
    try {
      docker.statsCmd(containerId).withNoStream(true).exec(new ResultCallback.Adapter<>() {
        @Override
        public void onNext(Statistics s) {
          statsRef.set(s);
        }
      }).awaitCompletion(6, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.debug("Stats unavailable for {}: {}", containerId, e.getMessage());
      return ContainerMetricsResponse.unavailable();
    }

    Statistics stats = statsRef.get();
    if (stats == null)
      return ContainerMetricsResponse.unavailable();

    // ── 2. CPU % ─────────────────────────────────────────────────────────
    double cpuPercent = 0.0;
    int cpuCores = 0;
    try {
      CpuStatsConfig curr = stats.getCpuStats();
      CpuStatsConfig prev = stats.getPreCpuStats();
      CpuUsageConfig cu = curr.getCpuUsage();
      CpuUsageConfig pu = prev.getCpuUsage();
      long cpuDelta = cu.getTotalUsage() - pu.getTotalUsage();
      long systemDelta = curr.getSystemCpuUsage() - prev.getSystemCpuUsage();
      Long cores = curr.getOnlineCpus();
      cpuCores = (cores != null && cores > 0)
          ? cores.intValue()
          : (cu.getPercpuUsage() != null ? cu.getPercpuUsage().size() : 1);
      if (systemDelta > 0 && cpuDelta >= 0) {
        cpuPercent = ((double) cpuDelta / systemDelta) * cpuCores * 100.0;
        cpuPercent = Math.min(cpuPercent, 100.0 * cpuCores); // clamp
      }
    } catch (Exception e) {
      log.debug("CPU calc failed for {}: {}", containerId, e.getMessage());
    }

    // ── 3. Memory ────────────────────────────────────────────────────────
    long memUsage = 0, memLimit = 0, memMaxUsage = 0;
    double memPercent = 0.0;
    try {
      MemoryStatsConfig mem = stats.getMemoryStats();
      memLimit = mem.getLimit() != null ? mem.getLimit() : 0;
      memMaxUsage = mem.getMaxUsage() != null ? mem.getMaxUsage() : 0;
      long rawUsage = mem.getUsage() != null ? mem.getUsage() : 0;
      // subtract page cache (Linux); Docker Desktop on macOS may not have cache stats
      long cache = 0L;
      try {
        if (mem.getStats() != null && mem.getStats().getCache() != null) {
          cache = mem.getStats().getCache();
        }
      } catch (Exception ignored) {
      }
      memUsage = rawUsage - cache;
      if (memLimit > 0)
        memPercent = (double) memUsage / memLimit * 100.0;
    } catch (Exception e) {
      log.debug("Memory calc failed for {}: {}", containerId, e.getMessage());
    }

    // ── 3b. CPU throttling ──────────────────────────────────────────────
    double cpuThrottledPct = 0.0;
    try {
      var currCpu = stats.getCpuStats();
      var prevCpu = stats.getPreCpuStats();
      if (currCpu != null && prevCpu != null && currCpu.getThrottlingData() != null
          && prevCpu.getThrottlingData() != null) {
        Long currT = currCpu.getThrottlingData().getThrottledTime();
        Long prevT = prevCpu.getThrottlingData().getThrottledTime();
        Long currP = currCpu.getThrottlingData().getPeriods();
        Long prevP = prevCpu.getThrottlingData().getPeriods();
        if (currT != null && prevT != null && currP != null && prevP != null) {
          long periodDelta = currP - prevP;
          long throttleDelta = currT - prevT;
          // throttledTime is in ns; periods × ~100ms each.
          if (periodDelta > 0) {
            double avgPeriodNs = 100_000_000.0; // default 100ms
            cpuThrottledPct = Math.min(100.0, ((double) throttleDelta / (periodDelta * avgPeriodNs)) * 100.0);
          }
        }
      }
    } catch (Exception ignored) {
    }

    // ── 4. Network I/O ───────────────────────────────────────────────────
    long netRx = 0, netTx = 0, netRxPkts = 0, netTxPkts = 0, netRxErr = 0, netTxErr = 0;
    try {
      Map<String, StatisticNetworksConfig> nets = stats.getNetworks();
      if (nets != null) {
        for (StatisticNetworksConfig n : nets.values()) {
          netRx += n.getRxBytes() != null ? n.getRxBytes() : 0;
          netTx += n.getTxBytes() != null ? n.getTxBytes() : 0;
          netRxPkts += n.getRxPackets() != null ? n.getRxPackets() : 0;
          netTxPkts += n.getTxPackets() != null ? n.getTxPackets() : 0;
          netRxErr += n.getRxErrors() != null ? n.getRxErrors() : 0;
          netTxErr += n.getTxErrors() != null ? n.getTxErrors() : 0;
        }
      }
    } catch (Exception e) {
      log.debug("Network stats failed for {}: {}", containerId, e.getMessage());
    }

    // ── 5. Block I/O ─────────────────────────────────────────────────────
    long blkRead = 0, blkWrite = 0, blkReadOps = 0, blkWriteOps = 0;
    try {
      BlkioStatsConfig blkio = stats.getBlkioStats();
      if (blkio != null && blkio.getIoServiceBytesRecursive() != null) {
        for (BlkioStatEntry e : blkio.getIoServiceBytesRecursive()) {
          if (e.getValue() == null)
            continue;
          if ("Read".equalsIgnoreCase(e.getOp()))
            blkRead += e.getValue();
          if ("Write".equalsIgnoreCase(e.getOp()))
            blkWrite += e.getValue();
        }
      }
      if (blkio != null && blkio.getIoServicedRecursive() != null) {
        for (BlkioStatEntry e : blkio.getIoServicedRecursive()) {
          if (e.getValue() == null)
            continue;
          if ("Read".equalsIgnoreCase(e.getOp()))
            blkReadOps += e.getValue();
          if ("Write".equalsIgnoreCase(e.getOp()))
            blkWriteOps += e.getValue();
        }
      }
    } catch (Exception e) {
      log.debug("Blkio stats failed for {}: {}", containerId, e.getMessage());
    }

    // ── 6. PIDs ──────────────────────────────────────────────────────────
    long pids = 0;
    try {
      if (stats.getPidsStats() != null && stats.getPidsStats().getCurrent() != null) {
        pids = stats.getPidsStats().getCurrent();
      }
    } catch (Exception ignored) {
    }

    // ── 7. Inspect (restart count + image + state + health + uptime) ────
    int restartCount = 0;
    String image = null;
    String containerState = "unknown";
    String healthStatus = "none";
    boolean oomKilled = false;
    String startedAtIso = null;
    long uptimeSecs = 0;
    long pidsLimit = 0;
    try {
      InspectContainerResponse inspect = docker.inspectContainerCmd(containerId).exec();
      restartCount = inspect.getRestartCount() != null ? inspect.getRestartCount() : 0;
      image = inspect.getConfig() != null ? inspect.getConfig().getImage() : null;
      var st = inspect.getState();
      if (st != null) {
        containerState = st.getStatus() != null ? st.getStatus() : "unknown";
        oomKilled = Boolean.TRUE.equals(st.getOOMKilled());
        startedAtIso = st.getStartedAt();
        if (Boolean.TRUE.equals(st.getRunning()) && startedAtIso != null) {
          try {
            long startMs = Instant.parse(startedAtIso).toEpochMilli();
            uptimeSecs = Math.max(0, (System.currentTimeMillis() - startMs) / 1000);
          } catch (Exception ignored) {
          }
        }
        if (st.getHealth() != null && st.getHealth().getStatus() != null) {
          healthStatus = st.getHealth().getStatus();
        }
      }
      if (inspect.getHostConfig() != null && inspect.getHostConfig().getPidsLimit() != null) {
        pidsLimit = Math.max(0L, inspect.getHostConfig().getPidsLimit());
      }
    } catch (Exception e) {
      log.debug("Inspect failed for {}: {}", containerId, e.getMessage());
    }

    // ── 8. Port probe ────────────────────────────────────────────────────
    boolean portReachable = false;
    long portLatencyMs = -1;
    if (hostPort > 0) {
      long t0 = System.currentTimeMillis();
      try (Socket sock = new Socket()) {
        sock.connect(new InetSocketAddress("localhost", hostPort), 800);
        portReachable = true;
        portLatencyMs = System.currentTimeMillis() - t0;
      } catch (IOException ignored) {
      }
    }

    return new ContainerMetricsResponse(true, Math.round(cpuPercent * 100.0) / 100.0, cpuCores,
        Math.round(cpuThrottledPct * 100.0) / 100.0, memUsage, memMaxUsage, memLimit,
        Math.round(memPercent * 100.0) / 100.0, netRx, netTx, netRxPkts, netTxPkts, netRxErr, netTxErr, blkRead,
        blkWrite, blkReadOps, blkWriteOps, pids, pidsLimit, restartCount, image, containerState, healthStatus,
        oomKilled, startedAtIso, uptimeSecs, portReachable, portLatencyMs, java.util.Map.of());
  }

  // ── Container exec ─────────────────────────────────────────────────────────

  /**
   * Runs a command inside a container and returns combined stdout/stderr as a
   * string, or {@code null} if the exec failed or exited non-zero. Times out
   * after {@code timeoutSeconds}. Used by {@link ToolMetricsProbe}.
   *
   * <p>
   * The {@code cmd} array is passed as-is to the Docker exec API and is never
   * expanded by a shell, so each element is treated as an opaque argument —
   * preventing command injection.
   */
  public String execCapture(String containerId, String[] cmd, int timeoutSeconds) {
    if (containerId == null || cmd == null || cmd.length == 0)
      return null;
    try {
      var created = docker.execCreateCmd(containerId).withCmd(cmd).withAttachStdout(true).withAttachStderr(true).exec();
      var buf = new java.io.ByteArrayOutputStream();
      boolean finished = docker.execStartCmd(created.getId()).exec(new ResultCallback.Adapter<Frame>() {
        @Override
        public void onNext(Frame frame) {
          try {
            buf.write(frame.getPayload());
          } catch (Exception ignored) {
          }
        }
      }).awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);
      if (!finished)
        return null;
      Long exit = docker.inspectExecCmd(created.getId()).exec().getExitCodeLong();
      if (exit != null && exit != 0L)
        return null;
      return buf.toString();
    } catch (Exception e) {
      log.debug("exec {} failed: {}", cmd[0], e.getMessage());
      return null;
    }
  }
}

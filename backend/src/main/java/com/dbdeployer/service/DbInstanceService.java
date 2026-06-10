package com.dbdeployer.service;

import com.dbdeployer.api.dto.ContainerMetricsResponse;
import com.dbdeployer.api.dto.DeployRequest;
import com.dbdeployer.api.dto.DiscoveredContainerDto;
import com.dbdeployer.api.dto.ImportRequest;
import com.dbdeployer.api.dto.InstanceStatsResponse;
import com.dbdeployer.api.dto.PipelineResponse;
import com.dbdeployer.api.dto.PipelineStepResponse;
import com.dbdeployer.api.dto.ReImportRequest;
import com.dbdeployer.deploy.BrewDeployEngine;
import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.deploy.OsDetector;
import com.dbdeployer.deploy.ToolMetricsProbe;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.DeploymentResponse;
import com.dbdeployer.model.ImageValidationDecision;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.pipeline.PipelineOrchestrator;
import com.dbdeployer.pipeline.store.DeploymentPipelineRepository;
import com.dbdeployer.pipeline.store.PipelineStepRepository;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;
import com.dbdeployer.validations.DeploymentValidations;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class DbInstanceService {

  private final BrewDeployEngine brew;
  private final OsDetector osDetector;
  private final DockerDeployEngine docker;
  private final ToolMetricsProbe toolMetrics;
  private final DeploymentValidations deploymentValidations;
  private final PipelineStepRepository stepRepo;
  private final PipelineOrchestrator orchestrator;
  private final ConnectionStringBuilder connBuilder;
  private final DeploymentConfigRepository configRepo;
  private final ImageValidationService imageValidation;
  private final DeployedContainerRepository containerRepo;
  private final DeploymentPipelineRepository pipelineRepo;

  public DbInstanceService(DeploymentConfigRepository configRepo, DeployedContainerRepository containerRepo,
      DockerDeployEngine docker, BrewDeployEngine brew, ConnectionStringBuilder connBuilder, OsDetector osDetector,
      PipelineOrchestrator orchestrator, DeploymentPipelineRepository pipelineRepo, PipelineStepRepository stepRepo,
      ImageValidationService imageValidation, ToolMetricsProbe toolMetrics,
      DeploymentValidations deploymentValidations) {
    this.configRepo = configRepo;
    this.containerRepo = containerRepo;
    this.docker = docker;
    this.brew = brew;
    this.connBuilder = connBuilder;
    this.osDetector = osDetector;
    this.orchestrator = orchestrator;
    this.pipelineRepo = pipelineRepo;
    this.stepRepo = stepRepo;
    this.imageValidation = imageValidation;
    this.toolMetrics = toolMetrics;
    this.deploymentValidations = deploymentValidations;
  }

  // ── Queries ────────────────────────────────────────────────────────────────

  /** Returns all deployed instances (excludes template rows). */
  public List<DeployedContainer> listAll() {
    return containerRepo.findAll();
  }

  /**
   * Aggregate status counts across all instances. Derived directly from the DB.
   */
  public InstanceStatsResponse getStats() {
    List<DeployedContainer> all = containerRepo.findAll();
    int running = 0, restarting = 0, stopped = 0, deploying = 0, removing = 0, error = 0, removed = 0, untracked = 0;
    for (DeployedContainer c : all) {
      switch (c.getStatus()) {
        case RUNNING -> running++;
        case RESTARTING -> restarting++;
        case STOPPED -> stopped++;
        case DEPLOYING -> deploying++;
        case REMOVING -> removing++;
        case ERROR -> error++;
        case REMOVED -> removed++;
        case UNTRACKED -> untracked++;
      }
    }
    int total = running + restarting + stopped + deploying + removing + error; // active only
    return new InstanceStatsResponse(total, running, restarting, stopped, deploying, removing, error, removed,
        untracked);
  }

  public DeployedContainer getById(String id) {
    return containerRepo.findByConfigId(id)
        .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + id));
  }

  /** Live Docker container metrics snapshot for a non-system instance. */
  public ContainerMetricsResponse getContainerMetrics(String configId) {
    DeployedContainer container = getById(configId);
    if (container == null || container.getContainerId() == null) {
      return ContainerMetricsResponse.unavailable();
    }
    ContainerMetricsResponse base = docker.getContainerMetrics(container.getContainerId(),
        container.getConfig().getHostPort());
    if (!base.available())
      return base;
    // Best-effort tool-specific telemetry (never blocks the response).
    java.util.Map<String, Object> tools = toolMetrics.collect(container.getConfig(), container.getContainerId());
    return tools.isEmpty() ? base : base.withToolMetrics(tools);
  }

  /**
   * Returns the most recent pipeline for an instance (or null if none exists).
   */
  public PipelineResponse getLatestPipeline(String configId) {
    return pipelineRepo.findTopByConfigIdOrderByCreatedAtDesc(configId).map(p -> {
      var steps = stepRepo.findByPipelineIdOrderByStepOrderAsc(p.getId()).stream().map(PipelineStepResponse::from)
          .toList();
      return PipelineResponse.from(p, steps);
    }).orElse(null);
  }

  // ── Deploy ─────────────────────────────────────────────────────────────────

  @Transactional
  public DeploymentResponse deploy(DeployRequest req, String configId) {
    log.info("[deploy] Request received: name='{}', dbType={}, version={}, hostPort={}", req.name(), req.dbType(),
        req.version(), req.hostPort());

    validateDeployRequest(req);

    var def = DatabaseCatalog.get(req.dbType());
    if (def == null) {
      log.warn("[deploy] Rejecting request: unsupported database type {}", req.dbType());
      throw new IllegalArgumentException("Unsupported database type: " + req.dbType());
    }

    // Validate image availability before creating any deployment or pipeline rows.
    var imageCheck = imageValidation.checkForDeploy(req.dbType(), req.version());
    log.info("[deploy] Image check result for {}:{} -> decision={}, local={}, hub={}, message='{}'", def.dockerImage(),
        req.version(), imageCheck.decision(), imageCheck.localStatus(), imageCheck.dockerHubStatus(),
        imageCheck.message());
    if (imageCheck.decision() == ImageValidationDecision.BLOCK) {
      log.warn("[deploy] Blocking deployment '{}' because image is not deployable: {}", req.name(),
          imageCheck.message());
      throw new IllegalArgumentException(imageCheck.message());
    }
    if (imageCheck.decision() == ImageValidationDecision.ALLOW_WITH_WARNING) {
      log.warn("Proceeding with deployment '{}' despite warning: {}", req.name(), imageCheck.message());
    }

    // Apply catalog defaults for any credentials the user left blank
    String username = resolveCredential(req.username(), def, DatabaseCatalog.EnvVarType.TEXT);
    String password = resolveCredential(req.password(), def, DatabaseCatalog.EnvVarType.PASSWORD);
    String databaseName = resolveCredential(req.databaseName(), def, DatabaseCatalog.EnvVarType.DATABASE);

    // ── Config row ──

    DeploymentConfig config = new DeploymentConfig();
    if (configId == null) {
      config.setId(UUID.randomUUID().toString());
      config.setName(req.name());
      config.setDbType(req.dbType());
      config.setVersion(req.version());
      config.setHostPort(req.hostPort());
      config.setContainerPort(def.defaultPort());
      config.setUsername(username);
      config.setPassword(password);
      config.setDatabaseName(databaseName);
      config.setDeployMethod(DeployMethod.DOCKER);
      config.setExtraEnvJson(req.extraEnvJson());
      config.setTemplateId(UUID.randomUUID().toString());
      config.setTemplate(true);
      config.setDeployCount(1);
      configRepo.save(config);
    } else {
      config = configRepo.findById(configId)
          .orElseThrow(() -> new IllegalArgumentException("Config not found: " + configId));
    }

    // ── Container row ── (starts as DEPLOYING; pipeline transitions it)
    DeployedContainer container = new DeployedContainer();
    container.setId(UUID.randomUUID().toString());
    container.setConfig(config);
    container.setContainerPort(req.hostPort());
    container.setStatus(InstanceStatus.DEPLOYING);
    containerRepo.save(container);

    // ── Create pipeline + fire async runner after commit ──
    orchestrator.createAndLaunch(config, container);
    containerRepo.save(container); // persist latestPipelineId set by orchestrator

    log.info("[deploy] Accepted deployment '{}' (configId={}, containerRecordId={}, pipelineId={})", config.getName(),
        config.getId(), container.getId(), container.getLatestPipelineId());

    return new DeploymentResponse(config, container);
  }

  private void validateDeployRequest(DeployRequest req) {
    if (configRepo.existsByName(req.name())) {
      log.warn("[deploy] Rejecting request: name already exists '{}'", req.name());
      throw new IllegalArgumentException("An instance named '" + req.name() + "' already exists");
    }

    if (containerRepo.existsByName(req.name())) {
      log.warn("[deploy] Rejecting request: name already exists '{}'", req.name());
      throw new IllegalArgumentException("An instance named '" + req.name() + "' already exists");
    }

    if (containerRepo.existsByHostPortAndNotRemoved(req.hostPort())) {
      log.warn("[deploy] Rejecting request: host port {} already in use", req.hostPort());
      throw new IllegalArgumentException("Port " + req.hostPort() + " is already in use");
    }
  }

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  @Transactional
  public DeploymentResponse startInstance(String configId) {
    DeployedContainer container = getById(configId);
    DeploymentConfig config = container.getConfig();
    requireNotSystem(config, "start");
    if (config.getDeployMethod() == DeployMethod.HOMEBREW) {
      brew.startServiceByContainerId(container.getContainerId(), container.getContainerName());
    } else {
      docker.start(container);
    }
    container.setStatus(InstanceStatus.RUNNING);
    if (config.getDeployMethod() == DeployMethod.HOMEBREW) {
      container.setStartedAt(Instant.now());
    } else {
      container.setStartedAt(docker.getStartedAt(container.getContainerId()));
    }
    containerRepo.save(container);
    return new DeploymentResponse(config, container);
  }

  @Transactional
  public DeploymentResponse stopInstance(String configId) {
    DeployedContainer container = getById(configId);
    DeploymentConfig config = container.getConfig();
    requireNotSystem(config, "stop");
    if (config.getDeployMethod() == DeployMethod.HOMEBREW) {
      brew.stopServiceByContainerId(container.getContainerId(), container.getContainerName());
    } else {
      docker.stop(container);
    }
    container.setStatus(InstanceStatus.STOPPED);
    containerRepo.save(container);
    return new DeploymentResponse(config, container);
  }

  @Transactional
  public void removeInstance(String configId) {
    DeployedContainer container = getById(configId);
    DeploymentConfig config = container.getConfig();
    requireNotSystem(config, "remove");

    container.setStatus(InstanceStatus.REMOVING);
    containerRepo.save(container);

    if (config.getDeployMethod() == DeployMethod.HOMEBREW) {
      log.info("Untracking Homebrew instance '{}' — Homebrew service left intact", config.getName());
    } else {
      try {
        log.info("Removing Docker container for instance '{}'", config.getName());
        docker.remove(container);
      } catch (Exception e) {
        log.warn("Docker remove failed for '{}' (may already be gone): {}", config.getName(), e.getMessage());
      }
      // Clean up volume data directory
      if (container.getDataDirectory() != null) {
        try {
          Path dataDir = Paths.get(container.getDataDirectory());
          deleteDirectoryRecursive(dataDir);
          log.info("Removed data directory: {}", dataDir);
        } catch (IOException e) {
          log.warn("Could not remove data directory for '{}': {}", config.getName(), e.getMessage());
        }
      }
    }

    // Mark container as REMOVED (retain for history) — do NOT delete the rows
    container.setStatus(InstanceStatus.REMOVED);
    container.setRemovedAt(Instant.now());
    containerRepo.save(container);
  }

  /**
   * Untrack an imported instance — marks it UNTRACKED without touching the Docker
   * container. The instance can be re-tracked at any time via
   * {@link #reTrackInstance(String)}.
   */
  @Transactional
  public void untrackInstance(String configId) {
    DeployedContainer container = getById(configId);
    DeploymentConfig config = container.getConfig();
    if (!config.isImported()) {
      throw new IllegalArgumentException("Only imported instances can be untracked");
    }
    log.info("Untracking imported instance '{}' — container preserved", config.getName());
    container.setStatus(InstanceStatus.UNTRACKED);
    containerRepo.save(container);
  }

  /**
   * Re-track a previously untracked imported instance — queries the live
   * Docker/Brew status and transitions the instance back to its real live status.
   */
  @Transactional
  public DeploymentResponse reTrackInstance(String configId) {
    DeployedContainer container = getById(configId);
    DeploymentConfig config = container.getConfig();
    if (container.getStatus() != InstanceStatus.UNTRACKED) {
      throw new IllegalArgumentException("Instance '" + config.getName() + "' is not currently untracked");
    }
    DeployMethod method = config.getDeployMethod() != null ? config.getDeployMethod() : DeployMethod.DOCKER;
    InstanceStatus liveStatus = method == DeployMethod.HOMEBREW
        ? brew.getServiceStatusByContainerId(container.getContainerId(), container.getContainerName())
        : docker.getStatus(container);
    log.info("Re-tracking instance '{}' — live status: {}", config.getName(), liveStatus);
    container.setStatus(liveStatus);
    containerRepo.save(container);
    return new DeploymentResponse(config, container);
  }

  // ── Import / Re-import ─────────────────────────────────────────────────────

  /**
   * Re-import an untracked (REMOVED) imported instance by associating it with a
   * new Docker container. All config (name, credentials, ports) is preserved;
   * only the container binding changes.
   */
  @Transactional
  public DeploymentResponse reImportInstance(String configId, ReImportRequest req) {
    DeployedContainer existing = getById(configId);
    DeploymentConfig config = existing.getConfig();

    if (!config.isImported()) {
      throw new IllegalArgumentException("Only imported instances can be re-imported");
    }
    if (existing.getStatus() != InstanceStatus.REMOVED) {
      throw new IllegalArgumentException("Instance '" + config.getName() + "' is not in REMOVED state");
    }

    // Check if the container ID is already tracked by a *different* instance
    containerRepo.findByContainerId(req.containerId()).ifPresent(other -> {
      if (!other.getId().equals(existing.getId())) {
        throw new IllegalArgumentException(
            "Container " + req.containerId().substring(0, 12) + " is already tracked by another instance");
      }
    });

    // Reuse the existing REMOVED row — update it in-place
    DeployMethod importMethod = detectImportMethod(req.containerId());
    config.setDeployMethod(importMethod);

    existing.setContainerId(req.containerId());
    existing.setContainerName(req.containerName());
    existing.setStatus(getImportedStatus(req.containerId(), req.containerName(), importMethod));
    existing.setStartedAt(getImportedStartedAt(req.containerId(), req.containerName(), importMethod));
    existing.setLatestPipelineId(null);
    containerRepo.save(existing);
    configRepo.save(config);

    log.info("Re-imported instance '{}' → container {}", config.getName(), req.containerId().substring(0, 12));
    return new DeploymentResponse(config, existing);
  }

  @Transactional
  public DeploymentResponse importContainer(ImportRequest req) {
    if (configRepo.existsByName(req.name())) {
      throw new IllegalArgumentException("An instance named '" + req.name() + "' already exists");
    }
    if (containerRepo.existsByContainerId(req.containerId())) {
      throw new IllegalArgumentException("Container " + req.containerId().substring(0, 12) + " is already tracked");
    }

    DbType dbType;
    try {
      dbType = DbType.valueOf(req.dbType());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown database type: " + req.dbType());
    }

    DeployMethod importMethod = detectImportMethod(req.containerId());

    // ── Config row ──
    DeploymentConfig config = new DeploymentConfig();
    config.setId(UUID.randomUUID().toString());
    config.setName(req.name());
    config.setDbType(dbType);
    config.setVersion(req.version() != null && !req.version().isBlank() ? req.version() : "unknown");
    config.setHostPort(req.hostPort());
    config.setContainerPort(req.containerPort());
    config.setUsername(req.username());
    config.setPassword(req.password());
    config.setDatabaseName(req.databaseName());
    config.setDeployMethod(importMethod);
    config.setImported(true);
    configRepo.save(config);

    // ── Container row ──
    DeployedContainer container = new DeployedContainer();
    container.setId(UUID.randomUUID().toString());
    container.setConfig(config);
    container.setContainerId(req.containerId());
    container.setContainerName(req.containerName());
    container.setStatus(getImportedStatus(req.containerId(), req.containerName(), importMethod));
    container.setStartedAt(getImportedStartedAt(req.containerId(), req.containerName(), importMethod));
    containerRepo.save(container);

    return new DeploymentResponse(config, container);
  }

  // ── Status sync ────────────────────────────────────────────────────────────

  @Transactional
  public void syncStatuses() {
    containerRepo.findByStatusNotIn(List.of(InstanceStatus.REMOVED, InstanceStatus.UNTRACKED)).forEach(container -> {
      // Skip containers still deploying with no containerId — DeploymentRecovery
      // handles
      // those on boot
      if (container.getContainerId() == null)
        return;
      DeployMethod method = container.getConfig() != null
          ? container.getConfig().getDeployMethod()
          : DeployMethod.DOCKER;

      InstanceStatus current = method == DeployMethod.HOMEBREW
          ? brew.getServiceStatusByContainerId(container.getContainerId(), container.getContainerName())
          : docker.getStatus(container);

      boolean changed = current != container.getStatus();
      if (changed)
        container.setStatus(current);
      if (current == InstanceStatus.RUNNING && container.getStartedAt() == null && method != DeployMethod.HOMEBREW) {
        Instant sa = docker.getStartedAt(container.getContainerId());
        if (sa != null) {
          container.setStartedAt(sa);
          changed = true;
        }
      }
      if (changed)
        containerRepo.save(container);
    });
  }

  // ── Discovery ──────────────────────────────────────────────────────────────

  public List<DiscoveredContainerDto> discoverContainers() {
    List<DeployedContainer> tracked = containerRepo.findAll();
    Set<String> trackedIds = tracked.stream().map(DeployedContainer::getContainerId).filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Set<String> trackedNames = tracked.stream().map(DeployedContainer::getContainerName).filter(Objects::nonNull)
        .collect(Collectors.toSet());

    List<DiscoveredContainerDto> discovered = new java.util.ArrayList<>(
        docker.discoverContainers(trackedIds, trackedNames));
    discovered.addAll(brew.discoverServices(trackedIds, trackedNames));
    return discovered;
  }

  // ── Misc ───────────────────────────────────────────────────────────────────

  @Transactional
  public DeploymentResponse rename(String configId, String newName) {
    if (newName == null || newName.isBlank())
      throw new IllegalArgumentException("Name cannot be blank");
    String trimmed = newName.trim();
    DeployedContainer container = getById(configId);
    DeploymentConfig config = container.getConfig();
    if (!trimmed.equals(config.getName()) && configRepo.existsByName(trimmed)) {
      throw new IllegalArgumentException("An instance named '" + trimmed + "' already exists");
    }
    container.setContainerName(trimmed); // keep container name in sync with instance name for easier identification
    containerRepo.save(container);
    return new DeploymentResponse(config, container);
    // return configRepo.save(config);
  }

  public String getConnectionString(String id) {
    return connBuilder.build(getById(id).getConfig());
  }

  public String getLogs(String configId, int tail) throws InterruptedException {
    DeployedContainer container = getById(configId);
    DeploymentConfig config = container.getConfig();
    if (config.getDeployMethod() == DeployMethod.HOMEBREW) {
      return "Logs are not available for Homebrew-managed services in this view. Use: brew services log "
          + (container.getContainerName() != null ? container.getContainerName() : "<service>");
    }
    return docker.getLogs(container, tail);
  }

  public OsDetector.SystemInfo getSystemInfo() {
    return osDetector.getSystemInfo();
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private void requireNotSystem(DeploymentConfig config, String action) {
    if (config.isSystem()) {
      throw new IllegalArgumentException(
          "The system database cannot be " + action + "ped. It is managed automatically by Port Wrangler.");
    }
  }

  private String resolveCredential(String supplied, DatabaseCatalog.DbDefinition def, DatabaseCatalog.EnvVarType type) {
    if (supplied != null && !supplied.isBlank())
      return supplied;
    return def.credentialEnvVars().stream().filter(ev -> ev.type() == type).map(DatabaseCatalog.EnvVar::placeholder)
        .findFirst().orElse(null);
  }

  private InstanceStatus getContainerStatus(String containerId) {
    DeployedContainer tmp = new DeployedContainer();
    tmp.setContainerId(containerId);
    return docker.getStatus(tmp);
  }

  private DeployMethod detectImportMethod(String containerId) {
    if (containerId != null && containerId.startsWith("brew:")) {
      return DeployMethod.HOMEBREW;
    }
    return DeployMethod.DOCKER;
  }

  private InstanceStatus getImportedStatus(String containerId, String containerName, DeployMethod method) {
    return method == DeployMethod.HOMEBREW
        ? brew.getServiceStatusByContainerId(containerId, containerName)
        : getContainerStatus(containerId);
  }

  private Instant getImportedStartedAt(String containerId, String containerName, DeployMethod method) {
    if (method == DeployMethod.HOMEBREW) {
      return null;
    }
    return docker.getStartedAt(containerId);
  }

  private void deleteDirectoryRecursive(Path path) throws IOException {
    if (!Files.exists(path))
      return;
    try (var walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.delete(p);
        } catch (IOException e) {
          log.warn("Could not delete {}: {}", p, e.getMessage());
        }
      });
    }
  }
}

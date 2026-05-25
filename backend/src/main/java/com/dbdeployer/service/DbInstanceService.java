package com.dbdeployer.service;

import com.dbdeployer.api.dto.*;
import com.dbdeployer.deploy.*;
import com.dbdeployer.model.*;
import com.dbdeployer.pipeline.PipelineOrchestrator;
import com.dbdeployer.pipeline.model.DeploymentPipeline;
import com.dbdeployer.pipeline.store.DeploymentPipelineRepository;
import com.dbdeployer.pipeline.store.PipelineStepRepository;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DbInstanceService {

    private static final Logger log = LoggerFactory.getLogger(DbInstanceService.class);

    private final DeploymentConfigRepository    configRepo;
    private final DeployedContainerRepository   containerRepo;
    private final DockerDeployEngine            docker;
    private final ConnectionStringBuilder       connBuilder;
    private final OsDetector                    osDetector;
    private final PipelineOrchestrator          orchestrator;
    private final DeploymentPipelineRepository  pipelineRepo;
    private final PipelineStepRepository        stepRepo;

    public DbInstanceService(DeploymentConfigRepository configRepo,
                             DeployedContainerRepository containerRepo,
                             DockerDeployEngine docker,
                             ConnectionStringBuilder connBuilder,
                             OsDetector osDetector,
                             PipelineOrchestrator orchestrator,
                             DeploymentPipelineRepository pipelineRepo,
                             PipelineStepRepository stepRepo) {
        this.configRepo    = configRepo;
        this.containerRepo = containerRepo;
        this.docker        = docker;
        this.connBuilder   = connBuilder;
        this.osDetector    = osDetector;
        this.orchestrator  = orchestrator;
        this.pipelineRepo  = pipelineRepo;
        this.stepRepo      = stepRepo;
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    /** Returns all configs (including removed ones — frontend decides what to show). */
    public List<DeploymentConfig> listAll() {
        return configRepo.findAll();
    }

    /** Aggregate status counts across all instances. Derived directly from the DB. */
    public InstanceStatsResponse getStats() {
        List<DeployedContainer> all = containerRepo.findAll();
        int running   = 0, stopped  = 0, deploying = 0,
            removing  = 0, error    = 0, removed   = 0;
        for (DeployedContainer c : all) {
            switch (c.getStatus()) {
                case RUNNING   -> running++;
                case STOPPED   -> stopped++;
                case DEPLOYING -> deploying++;
                case REMOVING  -> removing++;
                case ERROR     -> error++;
                case REMOVED   -> removed++;
            }
        }
        int total = running + stopped + deploying + removing + error; // active only
        return new InstanceStatsResponse(total, running, stopped, deploying, removing, error, removed);
    }

    public DeploymentConfig getById(String id) {
        return configRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + id));
    }

    /** Returns the most recent pipeline for an instance (or null if none exists). */
    public com.dbdeployer.api.dto.PipelineResponse getLatestPipeline(String configId) {
        return pipelineRepo.findTopByConfigIdOrderByCreatedAtDesc(configId)
                .map(p -> {
                    var steps = stepRepo.findByPipelineIdOrderByStepOrderAsc(p.getId())
                            .stream()
                            .map(com.dbdeployer.api.dto.PipelineStepResponse::from)
                            .toList();
                    return com.dbdeployer.api.dto.PipelineResponse.from(p, steps);
                })
                .orElse(null);
    }

    // ── Deploy ─────────────────────────────────────────────────────────────────

    @Transactional
    public DeploymentConfig deploy(DeployRequest req) {
        if (configRepo.existsByName(req.name())) {
            throw new IllegalArgumentException("An instance named '" + req.name() + "' already exists");
        }
        if (configRepo.existsByHostPort(req.hostPort())) {
            throw new IllegalArgumentException("Port " + req.hostPort() + " is already in use");
        }

        var def = DatabaseCatalog.get(req.dbType());
        if (def == null) throw new IllegalArgumentException("Unsupported database type: " + req.dbType());

        // Apply catalog defaults for any credentials the user left blank
        String username     = resolveCredential(req.username(),     def, DatabaseCatalog.EnvVarType.TEXT);
        String password     = resolveCredential(req.password(),     def, DatabaseCatalog.EnvVarType.PASSWORD);
        String databaseName = resolveCredential(req.databaseName(), def, DatabaseCatalog.EnvVarType.DATABASE);

        // ── Config row ──
        DeploymentConfig config = new DeploymentConfig();
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
        configRepo.save(config);

        // ── Container row ── (starts as DEPLOYING; pipeline transitions it)
        DeployedContainer container = new DeployedContainer();
        container.setId(UUID.randomUUID().toString());
        container.setConfig(config);
        container.setStatus(InstanceStatus.DEPLOYING);
        containerRepo.save(container);

        config.setContainer(container);

        // ── Create pipeline + fire async runner after commit ──
        orchestrator.createAndLaunch(config, container);
        containerRepo.save(container); // persist latestPipelineId set by orchestrator

        return config;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Transactional
    public DeploymentConfig startInstance(String id) {
        DeploymentConfig config    = getById(id);
        DeployedContainer container = requireContainer(config);
        requireNotSystem(config, "start");
        docker.start(container);
        container.setStatus(InstanceStatus.RUNNING);
        container.setStartedAt(docker.getStartedAt(container.getContainerId()));
        containerRepo.save(container);
        return config;
    }

    @Transactional
    public DeploymentConfig stopInstance(String id) {
        DeploymentConfig config    = getById(id);
        DeployedContainer container = requireContainer(config);
        requireNotSystem(config, "stop");
        docker.stop(container);
        container.setStatus(InstanceStatus.STOPPED);
        containerRepo.save(container);
        return config;
    }

    @Transactional
    public void removeInstance(String id) {
        DeploymentConfig config    = getById(id);
        DeployedContainer container = requireContainer(config);
        requireNotSystem(config, "remove");

        container.setStatus(InstanceStatus.REMOVING);
        containerRepo.save(container);

        if (config.isImported()) {
            log.info("Untracking imported instance '{}' — Docker container left intact", config.getName());
        } else {
            try {
                docker.remove(container);
            } catch (Exception e) {
                log.warn("Docker remove failed for '{}' (may already be gone): {}",
                        config.getName(), e.getMessage());
            }
            // Clean up volume data directory
            if (container.getDataDirectory() != null) {
                try {
                    Path dataDir = Paths.get(container.getDataDirectory());
                    deleteDirectoryRecursive(dataDir);
                    log.info("Removed data directory: {}", dataDir);
                } catch (IOException e) {
                    log.warn("Could not remove data directory for '{}': {}",
                            config.getName(), e.getMessage());
                }
            }
        }

        // Mark container as REMOVED (retain for history) — do NOT delete the rows
        container.setStatus(InstanceStatus.REMOVED);
        container.setRemovedAt(LocalDateTime.now());
        containerRepo.save(container);
    }

    // ── Import / Re-import ─────────────────────────────────────────────────────

    /**
     * Re-import an untracked (REMOVED) imported instance by associating it with a
     * new Docker container. All config (name, credentials, ports) is preserved;
     * only the container binding changes.
     */
    @Transactional
    public DeploymentConfig reImportInstance(String id, ReImportRequest req) {
        DeploymentConfig config    = getById(id);
        DeployedContainer existing = requireContainer(config);

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
        existing.setContainerId(req.containerId());
        existing.setContainerName(req.containerName());
        existing.setStatus(getContainerStatus(req.containerId()));
        existing.setStartedAt(docker.getStartedAt(req.containerId()));
        existing.setLatestPipelineId(null);
        containerRepo.save(existing);

        log.info("Re-imported instance '{}' → container {}", config.getName(), req.containerId().substring(0, 12));
        return config;
    }

    @Transactional
    public DeploymentConfig importContainer(ImportRequest req) {
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
        config.setDeployMethod(DeployMethod.DOCKER);
        config.setImported(true);
        configRepo.save(config);

        // ── Container row ──
        DeployedContainer container = new DeployedContainer();
        container.setId(UUID.randomUUID().toString());
        container.setConfig(config);
        container.setContainerId(req.containerId());
        container.setContainerName(req.containerName());
        container.setStatus(getContainerStatus(req.containerId()));
        container.setStartedAt(docker.getStartedAt(req.containerId()));
        containerRepo.save(container);

        config.setContainer(container);
        return config;
    }

    // ── Status sync ────────────────────────────────────────────────────────────

    @Transactional
    public void syncStatuses() {
        containerRepo.findByStatusNot(InstanceStatus.REMOVED).forEach(container -> {
            // Skip containers still deploying with no containerId — DeploymentRecovery handles those on boot
            if (container.getContainerId() == null) return;
            InstanceStatus current = docker.getStatus(container);
            boolean changed = current != container.getStatus();
            if (changed) container.setStatus(current);
            if (current == InstanceStatus.RUNNING && container.getStartedAt() == null) {
                LocalDateTime sa = docker.getStartedAt(container.getContainerId());
                if (sa != null) { container.setStartedAt(sa); changed = true; }
            }
            if (changed) containerRepo.save(container);
        });
    }

    // ── Discovery ──────────────────────────────────────────────────────────────

    public List<DiscoveredContainerDto> discoverContainers() {
        List<DeployedContainer> tracked = containerRepo.findAll();
        Set<String> trackedIds   = tracked.stream().map(DeployedContainer::getContainerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> trackedNames = tracked.stream().map(DeployedContainer::getContainerName)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return docker.discoverContainers(trackedIds, trackedNames);
    }

    // ── Misc ───────────────────────────────────────────────────────────────────

    @Transactional
    public DeploymentConfig rename(String id, String newName) {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("Name cannot be blank");
        String trimmed = newName.trim();
        DeploymentConfig config = getById(id);
        if (!trimmed.equals(config.getName()) && configRepo.existsByName(trimmed)) {
            throw new IllegalArgumentException("An instance named '" + trimmed + "' already exists");
        }
        config.setName(trimmed);
        return configRepo.save(config);
    }

    public String getConnectionString(String id) {
        return connBuilder.build(getById(id));
    }

    public String getLogs(String id, int tail) throws InterruptedException {
        DeployedContainer container = requireContainer(getById(id));
        return docker.getLogs(container, tail);
    }

    public OsDetector.SystemInfo getSystemInfo() {
        return osDetector.getSystemInfo();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void requireNotSystem(DeploymentConfig config, String action) {
        if (config.isSystem()) {
            throw new IllegalArgumentException(
                    "The system database cannot be " + action + "ped. It is managed automatically by DB Deployer.");
        }
    }

    private DeployedContainer requireContainer(DeploymentConfig config) {
        DeployedContainer c = config.getContainer();
        if (c == null) throw new IllegalStateException(
                "No container record found for instance '" + config.getName() + "'");
        return c;
    }

    private String resolveCredential(String supplied, DatabaseCatalog.DbDefinition def,
                                     DatabaseCatalog.EnvVarType type) {
        if (supplied != null && !supplied.isBlank()) return supplied;
        return def.credentialEnvVars().stream()
                .filter(ev -> ev.type() == type)
                .map(DatabaseCatalog.EnvVar::placeholder)
                .findFirst()
                .orElse(null);
    }

    private InstanceStatus getContainerStatus(String containerId) {
        DeployedContainer tmp = new DeployedContainer();
        tmp.setContainerId(containerId);
        return docker.getStatus(tmp);
    }

    private void deleteDirectoryRecursive(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); }
                    catch (IOException e) { log.warn("Could not delete {}: {}", p, e.getMessage()); }
                });
        }
    }
}

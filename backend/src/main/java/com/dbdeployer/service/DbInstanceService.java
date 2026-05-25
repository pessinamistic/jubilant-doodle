package com.dbdeployer.service;

import com.dbdeployer.api.dto.*;
import com.dbdeployer.deploy.*;
import com.dbdeployer.model.*;
import com.dbdeployer.store.DbInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DbInstanceService {

    private static final Logger log = LoggerFactory.getLogger(DbInstanceService.class);

    private final DbInstanceRepository repo;
    private final DockerDeployEngine docker;
    private final ConnectionStringBuilder connBuilder;
    private final OsDetector osDetector;
    private final AsyncDeployer asyncDeployer;

    public DbInstanceService(DbInstanceRepository repo,
                             DockerDeployEngine docker,
                             ConnectionStringBuilder connBuilder,
                             OsDetector osDetector,
                             AsyncDeployer asyncDeployer) {
        this.repo          = repo;
        this.docker        = docker;
        this.connBuilder   = connBuilder;
        this.osDetector    = osDetector;
        this.asyncDeployer = asyncDeployer;
    }

    public List<DbInstance> listAll() {
        return repo.findAll();
    }

    public DbInstance getById(String id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + id));
    }

    @Transactional
    public DbInstance deploy(DeployRequest req) {
        if (repo.existsByName(req.name())) {
            throw new IllegalArgumentException("An instance named '" + req.name() + "' already exists");
        }
        if (repo.existsByHostPort(req.hostPort())) {
            throw new IllegalArgumentException("Port " + req.hostPort() + " is already in use");
        }

        var def = DatabaseCatalog.get(req.dbType());
        if (def == null) {
            throw new IllegalArgumentException("Unsupported database type: " + req.dbType());
        }

        // Apply catalog defaults for any credentials the user left blank
        String username     = resolveCredential(req.username(),     def, DatabaseCatalog.EnvVarType.TEXT);
        String password     = resolveCredential(req.password(),     def, DatabaseCatalog.EnvVarType.PASSWORD);
        String databaseName = resolveCredential(req.databaseName(), def, DatabaseCatalog.EnvVarType.DATABASE);

        DbInstance instance = new DbInstance();
        instance.setId(UUID.randomUUID().toString());
        instance.setName(req.name());
        instance.setDbType(req.dbType());
        instance.setVersion(req.version());
        instance.setHostPort(req.hostPort());
        instance.setContainerPort(def.defaultPort());
        instance.setUsername(username);
        instance.setPassword(password);
        instance.setDatabaseName(databaseName);
        instance.setStatus(InstanceStatus.DEPLOYING);
        instance.setDeployMethod(DeployMethod.DOCKER);
        instance.setExtraEnvJson(req.extraEnvJson());

        repo.save(instance);
        asyncDeployer.deploy(instance.getId());   // runs on @Async thread — request returns immediately
        return instance;
    }

    /**
     * Returns the user-supplied value if non-blank, otherwise falls back to the
     * first catalog EnvVar placeholder matching the given type.
     */
    private String resolveCredential(String supplied, DatabaseCatalog.DbDefinition def,
                                     DatabaseCatalog.EnvVarType type) {
        if (supplied != null && !supplied.isBlank()) return supplied;
        return def.credentialEnvVars().stream()
                .filter(ev -> ev.type() == type)
                .map(DatabaseCatalog.EnvVar::placeholder)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public DbInstance startInstance(String id) {
        DbInstance instance = getById(id);
        requireNotSystem(instance, "start");
        docker.start(instance);
        instance.setStatus(InstanceStatus.RUNNING);
        instance.setStartedAt(docker.getStartedAt(instance.getContainerId()));
        return repo.save(instance);
    }

    @Transactional
    public DbInstance stopInstance(String id) {
        DbInstance instance = getById(id);
        requireNotSystem(instance, "stop");
        docker.stop(instance);
        instance.setStatus(InstanceStatus.STOPPED);
        return repo.save(instance);
    }

    @Transactional
    public void removeInstance(String id) {
        DbInstance instance = getById(id);
        requireNotSystem(instance, "remove");
        instance.setStatus(InstanceStatus.REMOVING);
        repo.save(instance);
        if (instance.isImported()) {
            // Imported containers are only untracked — the Docker container is left intact
            log.info("Untracking imported instance {} — container left intact", id);
        } else {
            try {
                docker.remove(instance);
            } catch (Exception e) {
                log.warn("Docker remove failed (may already be gone): {}", e.getMessage());
            }
        }
        repo.deleteById(id);
    }

    @Transactional
    public void syncStatuses() {
        repo.findAll().forEach(instance -> {
            if (instance.getContainerId() != null) {
                InstanceStatus current = docker.getStatus(instance);
                boolean changed = current != instance.getStatus();
                if (changed) {
                    instance.setStatus(current);
                }
                // Refresh startedAt when running
                if (current == InstanceStatus.RUNNING && instance.getStartedAt() == null) {
                    LocalDateTime sa = docker.getStartedAt(instance.getContainerId());
                    if (sa != null) {
                        instance.setStartedAt(sa);
                        changed = true;
                    }
                }
                if (changed) repo.save(instance);
            }
        });
    }

    /**
     * Returns Docker containers that are running but not yet tracked in db_instances.
     * Filters to DB-relevant images only.
     */
    public List<DiscoveredContainerDto> discoverContainers() {
        List<DbInstance> tracked = repo.findAll();
        Set<String> trackedIds   = tracked.stream().map(DbInstance::getContainerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> trackedNames = tracked.stream().map(DbInstance::getContainerName)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return docker.discoverContainers(trackedIds, trackedNames);
    }

    /**
     * Registers an existing Docker container as a managed db_instance row
     * without creating or modifying the container itself.
     */
    @Transactional
    public DbInstance importContainer(ImportRequest req) {
        if (repo.existsByName(req.name())) {
            throw new IllegalArgumentException("An instance named '" + req.name() + "' already exists");
        }
        if (repo.existsByContainerId(req.containerId())) {
            throw new IllegalArgumentException("Container " + req.containerId().substring(0, 12) + " is already tracked");
        }

        DbType dbType;
        try {
            dbType = DbType.valueOf(req.dbType());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown database type: " + req.dbType());
        }

        DbInstance instance = new DbInstance();
        instance.setId(UUID.randomUUID().toString());
        instance.setName(req.name());
        instance.setDbType(dbType);
        instance.setVersion(req.version() != null && !req.version().isBlank() ? req.version() : "unknown");
        instance.setHostPort(req.hostPort());
        instance.setContainerPort(req.containerPort());
        instance.setUsername(req.username());
        instance.setPassword(req.password());
        instance.setDatabaseName(req.databaseName());
        instance.setContainerId(req.containerId());
        instance.setContainerName(req.containerName());
        instance.setStatus(docker.getStatus(instance));
        instance.setDeployMethod(DeployMethod.DOCKER);
        instance.setStartedAt(docker.getStartedAt(req.containerId()));
        instance.setImported(true);  // imported containers are only untracked on remove

        return repo.save(instance);
    }

    @Transactional
    public DbInstance rename(String id, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        String trimmed = newName.trim();
        DbInstance instance = getById(id);
        if (!trimmed.equals(instance.getName()) && repo.existsByName(trimmed)) {
            throw new IllegalArgumentException("An instance named '" + trimmed + "' already exists");
        }
        instance.setName(trimmed);
        return repo.save(instance);
    }

    public String getConnectionString(String id) {        return connBuilder.build(getById(id));
    }

    public String getLogs(String id, int tail) throws InterruptedException {
        return docker.getLogs(getById(id), tail);
    }

    public OsDetector.SystemInfo getSystemInfo() {
        return osDetector.getSystemInfo();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void requireNotSystem(DbInstance instance, String action) {
        if (instance.isSystem()) {
            throw new IllegalArgumentException(
                "The system database cannot be " + action + "ped. It is managed automatically by DB Deployer.");
        }
    }
}

package com.dbdeployer.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "db_instances")
public class DbInstance {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @NotBlank
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "db_type", nullable = false, columnDefinition = "VARCHAR(50)")
    private DbType dbType;

    @NotBlank
    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "host_port", nullable = false)
    private int hostPort;

    @Column(name = "container_port", nullable = false)
    private int containerPort;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "database_name")
    private String databaseName;

    @Column(name = "container_id")
    private String containerId;

    @Column(name = "container_name")
    private String containerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InstanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "deploy_method", nullable = false)
    private DeployMethod deployMethod;

    @Column(name = "data_directory")
    private String dataDirectory;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "extra_env", columnDefinition = "TEXT")
    private String extraEnvJson;

    /** True for the auto-provisioned system Postgres — cannot be stopped/removed by users. */
    @Column(name = "is_system", nullable = false, columnDefinition = "boolean default false")
    private boolean isSystem = false;

    /** True when this instance was imported from an existing container (not deployed by DB Deployer).
     *  Removing an imported instance only untracks it — the Docker container is left intact. */
    @Column(name = "is_imported", nullable = false, columnDefinition = "boolean default false")
    private boolean isImported = false;

    /**
     * The last time the container was actually started by Docker.
     * Differs from createdAt when a container has been restarted.
     * Used to compute accurate uptime.
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public DbType getDbType() { return dbType; }
    public void setDbType(DbType dbType) { this.dbType = dbType; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public int getHostPort() { return hostPort; }
    public void setHostPort(int hostPort) { this.hostPort = hostPort; }

    public int getContainerPort() { return containerPort; }
    public void setContainerPort(int containerPort) { this.containerPort = containerPort; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }

    public InstanceStatus getStatus() { return status; }
    public void setStatus(InstanceStatus status) { this.status = status; }

    public DeployMethod getDeployMethod() { return deployMethod; }
    public void setDeployMethod(DeployMethod deployMethod) { this.deployMethod = deployMethod; }

    public String getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(String dataDirectory) { this.dataDirectory = dataDirectory; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getExtraEnvJson() { return extraEnvJson; }
    public void setExtraEnvJson(String extraEnvJson) { this.extraEnvJson = extraEnvJson; }

    public boolean isSystem() { return isSystem; }
    public void setSystem(boolean isSystem) { this.isSystem = isSystem; }

    public boolean isImported() { return isImported; }
    public void setImported(boolean isImported) { this.isImported = isImported; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
}

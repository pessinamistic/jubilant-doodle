package com.dbdeployer.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Tracks the Docker runtime state for one deployment of a {@link DeploymentConfig}.
 *
 * Lives in the {@code deployed_container} table. When a container is removed the row
 * is kept with {@code status = REMOVED} and {@code removedAt} set — providing a
 * permanent audit trail without losing the parent {@link DeploymentConfig}.
 */
@Entity
@Table(name = "deployed_container")
public class DeployedContainer {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    /** The config this container belongs to. */
    @OneToOne
    @JoinColumn(name = "config_id", nullable = false, updatable = false)
    private DeploymentConfig config;

    /** Docker container ID (64-char hex). Null while DEPLOYING. */
    @Column(name = "container_id")
    private String containerId;

    /** Human-readable Docker container name (e.g. {@code dbdeployer-my-redis}). */
    @Column(name = "container_name")
    private String containerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InstanceStatus status;

    /** Host-side data directory for volume mounts (e.g. {@code ~/.db-deployer/data/<id>}). */
    @Column(name = "data_directory")
    private String dataDirectory;

    /** When the Docker container was last started. Used to compute uptime. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** Set when the container transitions to {@link InstanceStatus#REMOVED}. */
    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public DeploymentConfig getConfig() { return config; }
    public void setConfig(DeploymentConfig config) { this.config = config; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }

    public InstanceStatus getStatus() { return status; }
    public void setStatus(InstanceStatus status) { this.status = status; }

    public String getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(String dataDirectory) { this.dataDirectory = dataDirectory; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime removedAt) { this.removedAt = removedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

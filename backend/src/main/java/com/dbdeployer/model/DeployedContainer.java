package com.dbdeployer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Tracks the Docker runtime state for one deployment of a
 * {@link DeploymentConfig}.
 *
 * <p>
 * Lives in the {@code deployed_container} table. When a container is removed
 * the row is kept with {@code status = REMOVED} and {@code removedAt} set —
 * providing a permanent audit trail without losing the parent
 * {@link DeploymentConfig}.
 */
@Setter
@Getter
@Entity
@ToString
@Table(name = "deployed_container")
public class DeployedContainer {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private String id;

  /** The config this container belongs to. */
  @ManyToOne // (fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "config_id", nullable = false, updatable = false)
  private DeploymentConfig config;

  /** Docker container ID (64-char hex). Null while DEPLOYING. */
  @Column(name = "container_id")
  private String containerId;

  /** Human-readable Docker container name (e.g. {@code dbdeployer-my-redis}). */
  @Column(name = "container_name", unique = true, nullable = false)
  private String containerName;

  // In DeployedContainer.java
  @Column(name = "host_port", nullable = false)
  private int hostPort;

  @Column(name = "container_port", nullable = false)
  private int containerPort;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private InstanceStatus status;

  /**
   * Host-side data directory for volume mounts (e.g.
   * {@code ~/.db-deployer/data/<id>}).
   */
  @Column(name = "data_directory")
  private String dataDirectory;

  /** When the Docker container was last started. Used to compute uptime. */
  @Column(name = "started_at")
  private Instant startedAt;

  /** Set when the container transitions to {@link InstanceStatus#REMOVED}. */
  @Column(name = "removed_at")
  private Instant removedAt;

  /**
   * ID of the most recent
   * {@link com.dbdeployer.pipeline.model.DeploymentPipeline} for this container.
   */
  @Column(name = "latest_pipeline_id")
  private String latestPipelineId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null)
      createdAt = Instant.now();
    if (updatedAt == null)
      updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}

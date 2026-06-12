package com.dbdeployer.pipeline.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;

/**
 * Persisted record of a single deploy pipeline run for one {@link
 * com.dbdeployer.model.DeploymentConfig}.
 *
 * <p>A new pipeline is created each time a deployment is triggered. The relationship to {@code
 * DeploymentConfig} is denormalized as a plain String {@code configId} so that pipeline rows remain
 * readable even if a config were ever deleted.
 */
@Data
@Entity
@Table(name = "deployment_pipeline")
public class DeploymentPipeline {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private String id;

  /** Foreign key to {@code deployment_config.id} — kept as a plain string (no FK join). */
  @Column(name = "config_id", nullable = false, updatable = false)
  private String configId;

  @Column(name = "deployment_container_id", nullable = false, updatable = false)
  private String deploymentContainerId;

  @Column(name = "is_template")
  private boolean isTemplate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PipelineStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "error_code")
  private DeployErrorCode errorCode;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }
}

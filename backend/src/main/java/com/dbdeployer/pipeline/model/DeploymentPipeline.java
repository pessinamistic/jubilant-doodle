package com.dbdeployer.pipeline.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persisted record of a single deploy pipeline run for one
 * {@link com.dbdeployer.model.DeploymentConfig}.
 *
 * <p>
 * A new pipeline is created each time a deploy is triggered. The relationship
 * to {@code
 * DeploymentConfig} is denormalised as a plain String {@code configId} so that
 * pipeline rows remain readable even if a config were ever deleted.
 */
@Entity
@Table(name = "deployment_pipeline")
public class DeploymentPipeline {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    /**
     * Foreign key to {@code deployment_config.id} — kept as a plain string (no FK
     * join).
     */
    @Column(name = "config_id", nullable = false, updatable = false)
    private String configId;

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

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public PipelineStatus getStatus() {
        return status;
    }

    public void setStatus(PipelineStatus status) {
        this.status = status;
    }

    public DeployErrorCode getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(DeployErrorCode ec) {
        this.errorCode = ec;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant t) {
        this.createdAt = t;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant t) {
        this.startedAt = t;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant t) {
        this.completedAt = t;
    }
}

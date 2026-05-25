package com.dbdeployer.pipeline.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Persisted record of a single deploy pipeline run for one {@link com.dbdeployer.model.DeploymentConfig}.
 *
 * A new pipeline is created each time a deploy is triggered. The relationship to
 * {@code DeploymentConfig} is denormalised as a plain String {@code configId} so that
 * pipeline rows remain readable even if a config were ever deleted.
 */
@Entity
@Table(name = "deployment_pipeline")
public class DeploymentPipeline {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    /** Foreign key to {@code deployment_config.id} — kept as a plain string (no FK join). */
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
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public String getId()                        { return id; }
    public void   setId(String id)               { this.id = id; }

    public String getConfigId()                  { return configId; }
    public void   setConfigId(String configId)   { this.configId = configId; }

    public PipelineStatus getStatus()            { return status; }
    public void           setStatus(PipelineStatus status) { this.status = status; }

    public DeployErrorCode getErrorCode()                    { return errorCode; }
    public void            setErrorCode(DeployErrorCode ec)  { this.errorCode = ec; }

    public String getErrorMessage()                      { return errorMessage; }
    public void   setErrorMessage(String errorMessage)   { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt()                  { return createdAt; }
    public void          setCreatedAt(LocalDateTime t)   { this.createdAt = t; }

    public LocalDateTime getStartedAt()                  { return startedAt; }
    public void          setStartedAt(LocalDateTime t)   { this.startedAt = t; }

    public LocalDateTime getCompletedAt()                { return completedAt; }
    public void          setCompletedAt(LocalDateTime t) { this.completedAt = t; }
}

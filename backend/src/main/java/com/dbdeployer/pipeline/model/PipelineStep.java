package com.dbdeployer.pipeline.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One step within a {@link DeploymentPipeline}.
 *
 * <p>
 * Steps are ordered by {@code stepOrder} and executed sequentially. If any step
 * fails, all subsequent PENDING steps are marked SKIPPED.
 */
@Entity
@Table(name = "pipeline_step")
public class PipelineStep {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private String id;

  /** Owning pipeline. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pipeline_id", nullable = false, updatable = false)
  private DeploymentPipeline pipeline;

  @Enumerated(EnumType.STRING)
  @Column(name = "step_type", nullable = false, updatable = false)
  private StepType stepType;

  /** Execution order (0-based). */
  @Column(name = "step_order", nullable = false, updatable = false)
  private int stepOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private StepStatus status;

  /** Success message or failure detail written by the step impl. */
  @Column(name = "message", length = 500)
  private String message;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  // ── Getters & Setters ──────────────────────────────────────────────────────

  public String getId() {
    return id;
  }

  public void setId(
    String id) {
    this.id = id;
  }

  public DeploymentPipeline getPipeline() {
    return pipeline;
  }

  public void setPipeline(
    DeploymentPipeline p) {
    this.pipeline = p;
  }

  public StepType getStepType() {
    return stepType;
  }

  public void setStepType(
    StepType t) {
    this.stepType = t;
  }

  public int getStepOrder() {
    return stepOrder;
  }

  public void setStepOrder(
    int order) {
    this.stepOrder = order;
  }

  public StepStatus getStatus() {
    return status;
  }

  public void setStatus(
    StepStatus s) {
    this.status = s;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(
    String msg) {
    this.message = msg;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(
    Instant t) {
    this.startedAt = t;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(
    Instant t) {
    this.completedAt = t;
  }
}

package com.dbdeployer.model;

import com.dbdeployer.runtime.GpuVendor;
import com.dbdeployer.runtime.ModelRuntime;
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
 * Registry row for a managed LLM runtime (the {@code model_runtime} table). One row is registered
 * when an {@link DbType#OLLAMA} deployment reaches RUNNING, recording the base URL the runtime's
 * HTTP API is published on and the GPU vendor it was configured with — so the model layer
 * (pullModel, ModelRouter, the runtime dashboard) can discover runtimes without re-probing Docker.
 */
@Data
@Entity
@Table(name = "model_runtime")
public class ModelRuntimeEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private String id;

  @Enumerated(EnumType.STRING)
  @Column(name = "runtime_type", nullable = false)
  private ModelRuntime runtimeType;

  /** The {@link DeploymentConfig} of the managed container backing this runtime. */
  @Column(name = "config_id")
  private String configId;

  /** Root of the runtime's HTTP API, e.g. {@code http://localhost:11434}. */
  @Column(name = "base_url", nullable = false)
  private String baseUrl;

  /** GPU vendor the container was configured with at deploy time. */
  @Enumerated(EnumType.STRING)
  @Column(name = "gpu_vendor")
  private GpuVendor gpuVendor;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }
}

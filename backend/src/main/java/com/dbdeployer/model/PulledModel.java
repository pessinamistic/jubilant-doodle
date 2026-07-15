package com.dbdeployer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;

/**
 * A model present in a managed LLM runtime (the {@code pulled_model} table), synced from the
 * runtime's local model list. Also carries the user's per-model inference settings (temperature,
 * context window, keep-alive) — NULL means "runtime default" — which {@code ModelRouter} applies on
 * every chat bound to this model.
 */
@Data
@Entity
@Table(name = "pulled_model")
public class PulledModel {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private String id;

  /** FK to {@link ModelRuntimeEntity} — the runtime this model lives in. */
  @Column(name = "runtime_id", nullable = false)
  private String runtimeId;

  /** Ollama tag, e.g. {@code llama3.1:8b}. */
  @Column(name = "model_name", nullable = false)
  private String modelName;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "quantization")
  private String quantization;

  @Column(name = "digest")
  private String digest;

  @Column(name = "pulled_at", nullable = false)
  private Instant pulledAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  // ── Per-model inference settings (V2) ─────────────────────────────────────

  @Column(name = "temperature")
  private Double temperature;

  /** Context window in tokens ({@code num_ctx}) — governs the model's memory footprint. */
  @Column(name = "num_ctx")
  private Integer numCtx;

  /** Ollama keep-alive: a duration ("5m", "1h"), "-1" = resident, "0" = unload after reply. */
  @Column(name = "keep_alive")
  private String keepAlive;

  @PrePersist
  void onCreate() {
    if (pulledAt == null) pulledAt = Instant.now();
  }
}

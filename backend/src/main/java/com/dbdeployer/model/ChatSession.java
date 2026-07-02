package com.dbdeployer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;

/**
 * One assistant conversation (the {@code chat_session} table) and its rolling-summary state — the
 * token-optimization counters that decide when older turns get re-compressed (roadmap §3): {@code
 * currentSeq} counts completed user/assistant turns, {@code summarizedThroughSeq} marks how far the
 * {@code rollingSummary} already covers.
 */
@Data
@Entity
@Table(name = "chat_session")
public class ChatSession {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private String id;

  @Column(name = "title")
  private String title;

  @Column(name = "system_prompt")
  private String systemPrompt;

  @Column(name = "model_runtime_id")
  private String modelRuntimeId;

  @Column(name = "model_id")
  private String modelId;

  /** Re-written (never appended) compression of turns 1..summarizedThroughSeq. */
  @Column(name = "rolling_summary")
  private String rollingSummary;

  @Column(name = "summary_token_count", nullable = false)
  private int summaryTokenCount;

  @Column(name = "summarized_through_seq", nullable = false)
  private int summarizedThroughSeq;

  /** Completed turns (one user message + one assistant reply). */
  @Column(name = "current_seq", nullable = false)
  private int currentSeq;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}

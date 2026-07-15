package com.dbdeployer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;

/**
 * One chat message (the {@code chat_message} table) — the retrievable corpus behind the RAG memory
 * layer. Distinct from the framework-owned {@code SPRING_AI_CHAT_MEMORY} verbatim window: this row
 * carries the retrieval signals ({@code accessCount}, {@code lastSeen}) that feed the
 * recency/frequency scorer.
 */
@Data
@Entity
@Table(name = "chat_message")
public class ChatMessage {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private String id;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  /** Session-scoped monotonic position (unique per session). */
  @Column(name = "seq", nullable = false)
  private int seq;

  /** USER | ASSISTANT | SYSTEM | TOOL. */
  @Column(name = "role", nullable = false)
  private String role;

  @Column(name = "content", nullable = false)
  private String content;

  @Column(name = "token_count", nullable = false)
  private int tokenCount;

  @Column(name = "access_count", nullable = false)
  private int accessCount;

  @Column(name = "last_seen")
  private Instant lastSeen;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }
}

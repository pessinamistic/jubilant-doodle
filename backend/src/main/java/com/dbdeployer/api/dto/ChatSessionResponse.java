package com.dbdeployer.api.dto;

import com.dbdeployer.model.ChatSession;
import java.time.Instant;

/** Summary row for the chat-history sidebar. */
public record ChatSessionResponse(
    String id,
    String title,
    String modelId,
    int turnCount,
    Instant createdAt,
    Instant updatedAt) {

  public static ChatSessionResponse from(ChatSession s) {
    return new ChatSessionResponse(
        s.getId(), s.getTitle(), s.getModelId(), s.getCurrentSeq(), s.getCreatedAt(), s.getUpdatedAt());
  }
}

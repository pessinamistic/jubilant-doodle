package com.dbdeployer.api.dto;

import com.dbdeployer.model.ChatMessage;
import java.time.Instant;

/** One persisted message when resuming a session. */
public record ChatMessageResponse(
    String id, int seq, String role, String content, Instant createdAt) {

  public static ChatMessageResponse from(ChatMessage m) {
    return new ChatMessageResponse(
        m.getId(), m.getSeq(), m.getRole(), m.getContent(), m.getCreatedAt());
  }
}

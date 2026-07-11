package com.dbdeployer.api;

import com.dbdeployer.ai.ChatSessionService;
import com.dbdeployer.ai.ChatToken;
import com.dbdeployer.ai.ModelSelection;
import com.dbdeployer.ai.RagChatService;
import com.dbdeployer.api.dto.ChatMessageResponse;
import com.dbdeployer.api.dto.ChatSessionResponse;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Server-Sent-Events chat endpoint plus session management (list / history / rename / delete).
 * Streaming returns a {@code Flux<ServerSentEvent>} directly from the servlet stack — Spring MVC
 * streams it over async I/O, no WebFlux required (roadmap §4.3).
 */
@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

  private final RagChatService chatService;
  private final ChatSessionService sessions;

  public ChatController(RagChatService chatService, ChatSessionService sessions) {
    this.chatService = chatService;
    this.sessions = sessions;
  }

  /**
   * Stream an assistant reply for a session. {@code model}/{@code baseUrl} are optional overrides.
   */
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<ChatToken>> stream(
      @RequestParam String sessionId,
      @RequestParam String message,
      @RequestParam(required = false) String model,
      @RequestParam(required = false) String baseUrl) {
    log.info("[api] chat stream: session={}, model={}", sessionId, model);
    return chatService.stream(sessionId, message, new ModelSelection(baseUrl, model));
  }

  /** All sessions for the history sidebar, most recently active first. */
  @GetMapping("/sessions")
  public List<ChatSessionResponse> listSessions() {
    return sessions.listSessions().stream().map(ChatSessionResponse::from).toList();
  }

  /** Full persisted transcript for resuming a session. */
  @GetMapping("/sessions/{id}/messages")
  public ResponseEntity<List<ChatMessageResponse>> history(@PathVariable String id) {
    return sessions
        .history(id)
        .map(msgs -> ResponseEntity.ok(msgs.stream().map(ChatMessageResponse::from).toList()))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Rename a session (body: {"title": "..."}). */
  @PatchMapping("/sessions/{id}")
  public ResponseEntity<ChatSessionResponse> rename(
      @PathVariable String id, @RequestBody Map<String, String> body) {
    return sessions
        .rename(id, body.get("title"))
        .map(s -> ResponseEntity.ok(ChatSessionResponse.from(s)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Delete a session, its messages, its verbatim memory window, and its vector memories. */
  @DeleteMapping("/sessions/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    return sessions.deleteSession(id)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }
}

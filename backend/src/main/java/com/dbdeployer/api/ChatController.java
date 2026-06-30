package com.dbdeployer.api;

import com.dbdeployer.ai.ChatToken;
import com.dbdeployer.ai.ModelSelection;
import com.dbdeployer.ai.RagChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Server-Sent-Events chat endpoint. Returns a {@code Flux<ServerSentEvent>} directly from the
 * servlet stack — Spring MVC streams it over async I/O, no WebFlux required (roadmap §4.3).
 */
@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

  private final RagChatService chatService;

  public ChatController(RagChatService chatService) {
    this.chatService = chatService;
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
}

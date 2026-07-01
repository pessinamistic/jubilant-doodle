package com.dbdeployer.api;

import com.dbdeployer.ai.AgentChatService;
import com.dbdeployer.ai.AgentEvent;
import com.dbdeployer.ai.ModelSelection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * SSE endpoint for the agentic chat: the model may call {@code InfrastructureTools}, and the stream
 * surfaces {@code tool_call}, {@code confirmation_request}, {@code token}, {@code error}, and
 * {@code done} events.
 *
 * <p>Confirmation flow: a first request ({@code approve=false}) that proposes a write/destructive
 * tool returns a {@code confirmation_request} and stops. The client then re-issues the same message
 * with {@code approve=true} to execute it.
 */
@Slf4j
@RestController
@RequestMapping("/chat/agent")
public class AgentChatController {

  private final AgentChatService agentChatService;

  public AgentChatController(AgentChatService agentChatService) {
    this.agentChatService = agentChatService;
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<AgentEvent>> stream(
      @RequestParam String message,
      @RequestParam(required = false) String model,
      @RequestParam(required = false) String baseUrl,
      @RequestParam(required = false, defaultValue = "false") boolean approve) {
    log.info("[api] agent stream: model={}, approve={}", model, approve);
    return agentChatService.stream(message, new ModelSelection(baseUrl, model), approve);
  }
}

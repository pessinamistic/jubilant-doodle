package com.dbdeployer.ai;

import com.dbdeployer.ai.tools.AgentSafety;
import com.dbdeployer.ai.tools.InfrastructureTools;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * The agentic chat loop (roadmap §5): the model proposes infrastructure {@link InfrastructureTools}
 * calls, and this service runs them through a <b>manual, confirmation-gated</b> execution loop.
 *
 * <p>Safety design:
 *
 * <ul>
 *   <li><b>internal execution disabled</b> — {@code internalToolExecutionEnabled(false)} means the
 *       model returns tool calls without running them; we decide what to run.
 *   <li><b>read-only tools auto-run</b>; a round containing any {@link
 *       AgentSafety#requiresConfirmation write/destructive} tool halts with a {@code
 *       confirmation_request} and is only executed when the caller re-requests with {@code
 *       approveWrites=true}.
 *   <li><b>read-only mode</b> ({@code portwrangler.agent.read-only=true}) strips write/destructive
 *       tools from the advertised set entirely — the model never even sees them.
 *   <li><b>runaway guard</b> — at most {@link AgentSafety#MAX_TOOL_ROUNDS} tool rounds per turn.
 * </ul>
 *
 * <p>The loop is sequential and blocking (each {@code call} hits the model), so events are computed
 * eagerly and emitted as a {@link Flux}; SSE event names distinguish the kinds.
 */
@Slf4j
@Service
public class AgentChatService {

  private final ModelRouter modelRouter;
  private final InfrastructureTools tools;
  private final ToolCallingManager toolCallingManager;
  private final AgentSafety safety;
  private final boolean readOnly;

  public AgentChatService(
      ModelRouter modelRouter,
      InfrastructureTools tools,
      ToolCallingManager toolCallingManager,
      AgentSafety safety,
      @Value("${portwrangler.agent.read-only:false}") boolean readOnly) {
    this.modelRouter = modelRouter;
    this.tools = tools;
    this.toolCallingManager = toolCallingManager;
    this.safety = safety;
    this.readOnly = readOnly;
  }

  /**
   * Run the agent for one user turn.
   *
   * @param approveWrites when {@code true}, write/destructive tools proposed this turn are
   *     executed; when {@code false} (the default first pass) they halt the loop with a {@code
   *     confirmation_request} so the caller can re-issue with approval.
   */
  public Flux<ServerSentEvent<AgentEvent>> stream(
      String userMessage, ModelSelection selection, boolean approveWrites) {

    ChatModel chatModel = modelRouter.chatModelFor(selection.baseUrl(), selection.modelId());
    List<ToolCallback> exposed = exposedCallbacks();
    ToolCallingChatOptions options =
        ToolCallingChatOptions.builder()
            .toolCallbacks(exposed)
            .internalToolExecutionEnabled(false)
            .build();

    // In read-only mode, write/destructive tools are never executed even with approval.
    boolean canExecuteWrites = approveWrites && !readOnly;

    List<ServerSentEvent<AgentEvent>> events = new ArrayList<>();
    Prompt prompt =
        new Prompt(
            List.of(
                new SystemMessage(ChatClientConfig.SYSTEM_PROMPT), new UserMessage(userMessage)),
            options);

    ChatResponse response = chatModel.call(prompt);
    int rounds = 0;

    while (response.hasToolCalls()) {
      if (++rounds > AgentSafety.MAX_TOOL_ROUNDS) {
        events.add(
            sse(
                "agent_error",
                AgentEvent.text(
                    "Tool-call limit ("
                        + AgentSafety.MAX_TOOL_ROUNDS
                        + " rounds) reached; stopping to avoid a runaway loop.")));
        return done(events);
      }

      List<ToolCall> calls = response.getResult().getOutput().getToolCalls();
      for (ToolCall c : calls) {
        events.add(sse("tool_call", AgentEvent.toolCall(c.name(), c.arguments())));
      }

      boolean needsConfirmation =
          calls.stream().anyMatch(c -> safety.requiresConfirmation(c.name()));
      if (needsConfirmation && !canExecuteWrites) {
        for (ToolCall c : calls) {
          if (safety.requiresConfirmation(c.name())) {
            events.add(sse("confirmation_request", AgentEvent.toolCall(c.name(), c.arguments())));
          }
        }
        // Halt before executing anything this round; the caller re-requests with
        // approveWrites=true.
        return done(events);
      }

      try {
        ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
        prompt = new Prompt(result.conversationHistory(), options);
        response = chatModel.call(prompt);
      } catch (RuntimeException e) {
        log.warn("[agent] tool execution failed: {}", e.getMessage());
        events.add(sse("agent_error", AgentEvent.text("Tool execution failed: " + e.getMessage())));
        return done(events);
      }
    }

    String text = response.getResult().getOutput().getText();
    if (text != null && !text.isBlank()) {
      events.add(sse("token", AgentEvent.text(text)));
    }
    return done(events);
  }

  /** The tool set advertised to the model — stripped to read-only when {@code readOnly} is set. */
  private List<ToolCallback> exposedCallbacks() {
    ToolCallback[] all =
        MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();
    if (!readOnly) {
      return Arrays.asList(all);
    }
    return Arrays.stream(all)
        .filter(cb -> safety.isReadOnly(cb.getToolDefinition().name()))
        .toList();
  }

  private static ServerSentEvent<AgentEvent> sse(String event, AgentEvent data) {
    return ServerSentEvent.<AgentEvent>builder(data).event(event).build();
  }

  private static Flux<ServerSentEvent<AgentEvent>> done(List<ServerSentEvent<AgentEvent>> events) {
    events.add(sse("done", AgentEvent.text("")));
    return Flux.fromIterable(events);
  }
}

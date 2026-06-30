package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbdeployer.ai.tools.AgentSafety;
import com.dbdeployer.ai.tools.InfrastructureTools;
import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * Verifies the agentic safety invariants without a live model: read-only tools auto-run, a
 * destructive tool never executes without approval, read-only mode never executes writes even with
 * approval, and the runaway loop is capped.
 */
@ExtendWith(MockitoExtension.class)
class AgentChatServiceTest {

  @Mock private ModelRouter modelRouter;
  @Mock private com.dbdeployer.service.DbInstanceService dbInstanceService;
  @Mock private ConnectionStringBuilder connBuilder;

  private InfrastructureTools tools;
  private final AgentSafety safety = new AgentSafety();
  // The real collaborator — deterministic, resolves tool callbacks from the prompt options.
  private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

  @BeforeEach
  void setUp() {
    tools = new InfrastructureTools(dbInstanceService, connBuilder);
  }

  private AgentChatService service(boolean readOnly) {
    return new AgentChatService(modelRouter, tools, toolCallingManager, safety, readOnly);
  }

  private List<ServerSentEvent<AgentEvent>> run(boolean readOnly, String message, boolean approve) {
    return service(readOnly).stream(message, new ModelSelection(null, null), approve)
        .collectList()
        .block();
  }

  // ── Stub model helpers ────────────────────────────────────────────────────────

  private static ChatResponse toolResponse(String name, String args) {
    ToolCall tc = new ToolCall("call-" + name, "function", name, args);
    return new ChatResponse(
        List.of(new Generation(new AssistantMessage("", Map.of(), List.of(tc)))));
  }

  private static ChatResponse textResponse(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  /** A model that returns each scripted response in turn. */
  private static ChatModel scripted(ChatResponse... script) {
    java.util.Deque<ChatResponse> queue = new java.util.ArrayDeque<>(List.of(script));
    return new ChatModel() {
      @Override
      public ChatResponse call(Prompt prompt) {
        ChatResponse r = queue.poll();
        if (r == null) {
          throw new IllegalStateException("scripted model exhausted");
        }
        return r;
      }

      @Override
      public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
      }
    };
  }

  /** A model that always proposes the same tool call — used to exercise the loop cap. */
  private static ChatModel alwaysToolCall(String name, String args) {
    return new ChatModel() {
      @Override
      public ChatResponse call(Prompt prompt) {
        return toolResponse(name, args);
      }

      @Override
      public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
      }
    };
  }

  private static List<String> eventNames(List<ServerSentEvent<AgentEvent>> events) {
    return events.stream().map(ServerSentEvent::event).toList();
  }

  // ── Tests ─────────────────────────────────────────────────────────────────────

  @Test
  void destructive_tool_is_not_executed_without_approval() {
    // Model proposes removeInstance; first pass (approve=false) must halt before execution.
    when(modelRouter.chatModelFor(any(), any()))
        .thenReturn(scripted(toolResponse("removeInstance", "{\"instanceName\":\"redis\"}")));

    List<ServerSentEvent<AgentEvent>> events = run(false, "delete the redis instance", false);

    assertThat(eventNames(events)).containsExactly("tool_call", "confirmation_request", "done");
    // The underlying destructive operation must never run.
    verify(dbInstanceService, never()).removeInstance(anyString());
    AgentEvent confirm = events.get(1).data();
    assertThat(confirm.tool()).isEqualTo("removeInstance");
  }

  @Test
  void destructive_tool_executes_once_approved() {
    DeployedContainer redis = container("redis-id", "redis");
    when(dbInstanceService.listAll()).thenReturn(List.of(redis));
    when(modelRouter.chatModelFor(any(), any()))
        .thenReturn(
            scripted(
                toolResponse("removeInstance", "{\"instanceName\":\"redis\"}"),
                textResponse("Done — redis removed.")));

    List<ServerSentEvent<AgentEvent>> events = run(false, "delete redis", true);

    verify(dbInstanceService).removeInstance("redis-id");
    assertThat(eventNames(events)).containsExactly("tool_call", "token", "done");
    assertThat(events.get(1).data().text()).contains("redis removed");
  }

  @Test
  void readonly_mode_never_executes_a_write_even_when_approved() {
    // read-only strips removeInstance from the advertised set; gate also refuses it. Either way the
    // destructive op must not run.
    when(modelRouter.chatModelFor(any(), any()))
        .thenReturn(scripted(toolResponse("removeInstance", "{\"instanceName\":\"redis\"}")));

    List<ServerSentEvent<AgentEvent>> events = run(true, "delete redis", true);

    verify(dbInstanceService, never()).removeInstance(anyString());
    assertThat(eventNames(events)).contains("confirmation_request", "done");
  }

  @Test
  void readonly_tool_auto_runs_then_returns_text() {
    when(dbInstanceService.listAll()).thenReturn(List.of());
    when(modelRouter.chatModelFor(any(), any()))
        .thenReturn(
            scripted(toolResponse("listInstances", "{}"), textResponse("You have no instances.")));

    List<ServerSentEvent<AgentEvent>> events = run(false, "what's running?", false);

    assertThat(eventNames(events)).containsExactly("tool_call", "token", "done");
    assertThat(events.get(0).data().tool()).isEqualTo("listInstances");
    assertThat(events.get(1).data().text()).contains("no instances");
  }

  @Test
  void runaway_tool_loop_is_capped() {
    when(dbInstanceService.listAll()).thenReturn(List.of());
    // Model never stops proposing a (read-only) tool call.
    when(modelRouter.chatModelFor(any(), any())).thenReturn(alwaysToolCall("listInstances", "{}"));

    List<ServerSentEvent<AgentEvent>> events = run(false, "loop forever", false);

    assertThat(events).isNotNull();
    // Last event is done; an error event reports the cap; no infinite hang.
    assertThat(eventNames(events)).contains("error", "done");
    assertThat(events.get(events.size() - 1).event()).isEqualTo("done");
    AgentEvent error =
        events.stream()
            .filter(e -> "error".equals(e.event()))
            .map(ServerSentEvent::data)
            .findFirst()
            .orElseThrow();
    assertThat(error.text()).contains("Tool-call limit");
    // Tool ran at most MAX_TOOL_ROUNDS times before the cap fired.
    verify(dbInstanceService, org.mockito.Mockito.times(AgentSafety.MAX_TOOL_ROUNDS)).listAll();
  }

  // ── Fixtures ────────────────────────────────────────────────────────────────

  private static DeployedContainer container(String id, String name) {
    DeploymentConfig cfg = new DeploymentConfig();
    cfg.setName(name);
    cfg.setDbType(DbType.REDIS);
    cfg.setVersion("7");
    DeployedContainer dc = new DeployedContainer();
    dc.setId(id);
    dc.setConfig(cfg);
    dc.setStatus(InstanceStatus.RUNNING);
    return dc;
  }
}

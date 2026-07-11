package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class RagChatServiceTest {

  @Mock private ModelRouter modelRouter;
  @Mock private MemoryRetriever memoryRetriever;
  @Mock private ChatSessionService chatSessions;
  @Mock private RollingSummaryWorker summaryWorker;
  @Mock private ChatMemoryIngestionService memoryIngestion;

  private final SmartContextBuilder smartContextBuilder = new SmartContextBuilder();

  /** A ChatModel that streams the supplied chunks as assistant tokens. */
  static ChatModel stubModel(String... chunks) {
    return new ChatModel() {
      @Override
      public ChatResponse call(Prompt prompt) {
        return new ChatResponse(
            List.of(new Generation(new AssistantMessage(String.join("", chunks)))));
      }

      @Override
      public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.fromArray(chunks)
            .map(c -> new ChatResponse(List.of(new Generation(new AssistantMessage(c)))));
      }
    };
  }

  /**
   * A ChatModel that records the prompt it was streamed, so we can assert on the system message.
   */
  static ChatModel capturingModel(AtomicReference<Prompt> sink) {
    return new ChatModel() {
      @Override
      public ChatResponse call(Prompt prompt) {
        sink.set(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
      }

      @Override
      public Flux<ChatResponse> stream(Prompt prompt) {
        sink.set(prompt);
        return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));
      }
    };
  }

  private RagChatService service() {
    lenient().when(chatSessions.rollingSummaryFor(any())).thenReturn(Optional.empty());
    return new RagChatService(
        modelRouter,
        memoryRetriever,
        smartContextBuilder,
        chatSessions,
        summaryWorker,
        memoryIngestion);
  }

  @Test
  void streams_tokens_then_a_done_event() {
    when(modelRouter.clientFor(any(), any()))
        .thenReturn(ChatClient.builder(stubModel("Hel", "lo")).build());
    when(memoryRetriever.retrieve(any(), any(), anyInt())).thenReturn(List.of());

    List<ServerSentEvent<ChatToken>> events =
        service().stream("s1", "hi", new ModelSelection(null, null)).collectList().block();

    assertThat(events).isNotNull();
    // 2 token events + 1 done event
    assertThat(events).hasSize(3);
    assertThat(events.get(0).event()).isEqualTo("token");
    assertThat(events.get(0).data().token()).isEqualTo("Hel");
    assertThat(events.get(1).data().token()).isEqualTo("lo");
    assertThat(events.get(2).event()).isEqualTo("done");
  }

  @Test
  void injects_retrieved_memory_into_system_prompt_without_clobbering_base() {
    AtomicReference<Prompt> captured = new AtomicReference<>();
    when(modelRouter.clientFor(any(), any()))
        .thenReturn(ChatClient.builder(capturingModel(captured)).build());
    when(memoryRetriever.retrieve(any(), any(), anyInt()))
        .thenReturn(
            List.of(
                new ScoredChunk("redis container was OOM killed", Map.of(), 0.9, null, 0, 0.9)));

    service().stream("s1", "why did redis die", new ModelSelection(null, null))
        .collectList()
        .block();

    assertThat(captured.get()).isNotNull();
    String system =
        captured.get().getInstructions().stream()
            .filter(m -> m instanceof SystemMessage)
            .map(Message::getText)
            .collect(Collectors.joining());

    assertThat(system).contains("Port Wrangler"); // base prompt preserved
    assertThat(system).contains("redis container was OOM killed"); // memory injected
  }

  @Test
  void persists_the_turn_and_triggers_summarisation_on_completion() {
    when(modelRouter.clientFor(any(), any()))
        .thenReturn(ChatClient.builder(stubModel("Hel", "lo")).build());
    when(memoryRetriever.retrieve(any(), any(), anyInt())).thenReturn(List.of());
    var session = new com.dbdeployer.model.ChatSession();
    session.setId("s1");
    when(chatSessions.recordTurn(eq("s1"), eq("hi"), eq("Hello"), any())).thenReturn(session);

    service().stream("s1", "hi", new ModelSelection(null, null)).collectList().block();

    verify(chatSessions).recordTurn(eq("s1"), eq("hi"), eq("Hello"), any());
    verify(summaryWorker).summariseIfNeeded(session);
  }

  @Test
  void injects_the_rolling_summary_into_the_system_prompt() {
    AtomicReference<Prompt> captured = new AtomicReference<>();
    when(modelRouter.clientFor(any(), any()))
        .thenReturn(ChatClient.builder(capturingModel(captured)).build());
    when(memoryRetriever.retrieve(any(), any(), anyInt())).thenReturn(List.of());
    when(chatSessions.rollingSummaryFor("s1"))
        .thenReturn(Optional.of("user deployed postgres 'orders' on 5544"));

    new RagChatService(
            modelRouter,
            memoryRetriever,
            smartContextBuilder,
            chatSessions,
            summaryWorker,
            memoryIngestion)
        .stream("s1", "what did I deploy?", new ModelSelection(null, null)).collectList().block();

    String system =
        captured.get().getInstructions().stream()
            .filter(m -> m instanceof SystemMessage)
            .map(Message::getText)
            .collect(Collectors.joining());
    assertThat(system).contains("user deployed postgres 'orders' on 5544");
  }

  @Test
  void degrades_gracefully_when_retrieval_throws() {
    when(modelRouter.clientFor(any(), any()))
        .thenReturn(ChatClient.builder(stubModel("ok")).build());
    when(memoryRetriever.retrieve(any(), any(), anyInt()))
        .thenThrow(new RuntimeException("pgvector down"));

    List<ServerSentEvent<ChatToken>> events =
        service().stream("s1", "hi", new ModelSelection(null, null)).collectList().block();

    assertThat(events).isNotNull();
    // 1 token event + 1 done event — stream is unbroken despite retrieval failure
    assertThat(events).hasSize(2);
    assertThat(events.get(0).data().token()).isEqualTo("ok");
    assertThat(events.get(1).event()).isEqualTo("done");
  }
}

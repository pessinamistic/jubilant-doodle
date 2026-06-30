package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class RagChatServiceTest {

  @Mock private ModelRouter modelRouter;

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

  @Test
  void streams_tokens_then_a_done_event() {
    when(modelRouter.clientFor(any(), any()))
        .thenReturn(ChatClient.builder(stubModel("Hel", "lo")).build());

    List<ServerSentEvent<ChatToken>> events =
        new RagChatService(modelRouter)
            .stream("s1", "hi", new ModelSelection(null, null)).collectList().block();

    assertThat(events).isNotNull();
    // 2 token events + 1 done event
    assertThat(events).hasSize(3);
    assertThat(events.get(0).event()).isEqualTo("token");
    assertThat(events.get(0).data().token()).isEqualTo("Hel");
    assertThat(events.get(1).data().token()).isEqualTo("lo");
    assertThat(events.get(2).event()).isEqualTo("done");
  }
}

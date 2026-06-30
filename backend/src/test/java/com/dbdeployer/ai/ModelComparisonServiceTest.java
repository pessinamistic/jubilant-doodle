package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;

@ExtendWith(MockitoExtension.class)
class ModelComparisonServiceTest {

  @Mock private ModelRouter modelRouter;

  @Test
  void merges_two_model_streams_each_tagged_with_its_slot_and_model() {
    when(modelRouter.statelessClientFor(any(), eq("llama3.1:8b")))
        .thenReturn(ChatClient.builder(RagChatServiceTest.stubModel("a")).build());
    when(modelRouter.statelessClientFor(any(), eq("mistral:7b")))
        .thenReturn(ChatClient.builder(RagChatServiceTest.stubModel("b")).build());

    List<ServerSentEvent<ComparisonChunk>> events =
        new ModelComparisonService(modelRouter)
            .compare("hello", null, "llama3.1:8b", "mistral:7b", null)
            .collectList()
            .block();

    assertThat(events).isNotNull().hasSize(2);
    assertThat(events)
        .extracting(e -> e.data().slot() + ":" + e.data().model() + ":" + e.data().text())
        .containsExactlyInAnyOrder("A:llama3.1:8b:a", "B:mistral:7b:b");
  }

  @Test
  void includes_third_model_when_provided() {
    when(modelRouter.statelessClientFor(any(), eq("a")))
        .thenReturn(ChatClient.builder(RagChatServiceTest.stubModel("x")).build());
    when(modelRouter.statelessClientFor(any(), eq("b")))
        .thenReturn(ChatClient.builder(RagChatServiceTest.stubModel("y")).build());
    when(modelRouter.statelessClientFor(any(), eq("c")))
        .thenReturn(ChatClient.builder(RagChatServiceTest.stubModel("z")).build());

    var events =
        new ModelComparisonService(modelRouter)
            .compare("p", null, "a", "b", "c")
            .collectList()
            .block();

    assertThat(events).hasSize(3);
    assertThat(events).extracting(e -> e.data().slot()).containsExactlyInAnyOrder("A", "B", "C");
  }
}

package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class RollingSummaryServiceTest {

  private final RollingSummaryService service = new RollingSummaryService();

  @Test
  void fires_after_four_new_turns() {
    assertThat(RollingSummaryService.shouldSummarise(4, 0, 10)).isTrue();
    assertThat(RollingSummaryService.shouldSummarise(3, 0, 10)).isFalse();
  }

  @Test
  void fires_when_verbatim_token_budget_exceeded_even_with_few_turns() {
    assertThat(RollingSummaryService.shouldSummarise(1, 0, 1500)).isTrue();
    assertThat(RollingSummaryService.shouldSummarise(1, 0, 1499)).isFalse();
  }

  @Test
  void prompt_uses_none_placeholder_for_blank_prior_summary() {
    String p = RollingSummaryService.buildUserPrompt("  ", "U: deploy redis");
    assertThat(p).contains("PRIOR SUMMARY:\n(none)").contains("NEW TURNS:\nU: deploy redis");
  }

  @Test
  void summarise_invokes_the_model_and_returns_its_text() {
    ChatModel stub =
        new ChatModel() {
          @Override
          public ChatResponse call(Prompt prompt) {
            return new ChatResponse(
                List.of(new Generation(new AssistantMessage("- redis 'cache' on 6390"))));
          }

          @Override
          public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
          }
        };

    String summary =
        service.summarise(ChatClient.builder(stub).build(), "(none)", "U: deploy redis cache");

    assertThat(summary).isEqualTo("- redis 'cache' on 6390");
  }
}

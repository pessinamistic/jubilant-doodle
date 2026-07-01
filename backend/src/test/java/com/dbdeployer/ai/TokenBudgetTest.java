package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

class TokenBudgetTest {

  private final TokenBudget budget = new TokenBudget();

  @Test
  void estimates_roughly_chars_over_four() {
    assertThat(budget.estimate("12345678")).isEqualTo(2); // 8/4
    assertThat(budget.estimate("123456789")).isEqualTo(3); // ceil(9/4)
  }

  @Test
  void empty_and_null_are_zero() {
    assertThat(budget.estimate("")).isZero();
    assertThat(budget.estimate(null)).isZero();
  }

  @Test
  void sums_across_messages() {
    List<Message> messages = List.of(new UserMessage("12345678"), new AssistantMessage("1234"));
    assertThat(budget.estimateMessages(messages)).isEqualTo(3); // 2 + 1
  }
}

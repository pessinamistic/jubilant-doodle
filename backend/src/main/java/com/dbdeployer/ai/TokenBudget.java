package com.dbdeployer.ai;

import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

/**
 * Approximate token estimation for budget triggers (roadmap §3.7). Exactness is not required — the
 * estimate only needs to be roughly right to drive the summarisation trigger. Uses a {@code
 * chars/4} heuristic, which is close enough across Ollama models without coupling to a specific
 * tokenizer.
 */
@Component
public class TokenBudget {

  private static final double CHARS_PER_TOKEN = 4.0;

  public int estimate(String text) {
    if (text == null || text.isEmpty()) return 0;
    return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
  }

  public int estimateMessages(List<Message> messages) {
    if (messages == null) return 0;
    return messages.stream().mapToInt(m -> estimate(m.getText())).sum();
  }
}

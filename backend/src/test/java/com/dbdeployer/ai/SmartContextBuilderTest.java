package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmartContextBuilderTest {

  private final SmartContextBuilder builder = new SmartContextBuilder();

  private static ScoredChunk mem(String content) {
    return new ScoredChunk(content, Map.of(), 0.9, Instant.now(), 1, 0.9);
  }

  @Test
  void empty_when_no_summary_and_no_memories() {
    assertThat(builder.build(null, List.of())).isEmpty();
    assertThat(builder.build("  ", null)).isEmpty();
  }

  @Test
  void includes_summary_section_only() {
    String block = builder.build("- redis 'cache' on 6390", List.of());
    assertThat(block).contains("### Session summary").contains("redis 'cache' on 6390");
    assertThat(block).doesNotContain("### Relevant memories");
  }

  @Test
  void includes_memories_section_only() {
    String block = builder.build(null, List.of(mem("deployment cache type=REDIS port=6390")));
    assertThat(block).contains("### Relevant memories").contains("- deployment cache type=REDIS");
    assertThat(block).doesNotContain("### Session summary");
  }

  @Test
  void combines_summary_and_memories_and_collapses_whitespace() {
    String block =
        builder.build("summary text", List.of(mem("line one\n   line two\twith   tabs")));
    assertThat(block).contains("## Conversation context");
    assertThat(block).contains("- line one line two with tabs"); // whitespace collapsed
  }

  @Test
  void truncates_very_long_memory_lines() {
    String longText = "x".repeat(1000);
    String block = builder.build(null, List.of(mem(longText)));
    assertThat(block).contains("…");
    assertThat(block.length()).isLessThan(longText.length());
  }
}

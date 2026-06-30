package com.dbdeployer.ai;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Assembles the per-turn "smart context" block (roadmap §3.1): the rolling session summary plus the
 * top-k retrieved memories, rendered as a single {@code SystemMessage}-ready string that sits ahead
 * of the verbatim window. Because the summary is bounded and retrieval is fixed-k, this block has
 * roughly constant size — which is what flattens per-message token cost.
 *
 * <p>Pure and unit-testable; the caller injects it as a system message before the user turn.
 */
@Component
public class SmartContextBuilder {

  private static final int MAX_MEMORY_CHARS = 280;

  /** Returns the context block, or an empty string when there is nothing to add. */
  public String build(String rollingSummary, List<ScoredChunk> memories) {
    boolean hasSummary = rollingSummary != null && !rollingSummary.isBlank();
    boolean hasMemories = memories != null && !memories.isEmpty();
    if (!hasSummary && !hasMemories) return "";

    StringBuilder sb = new StringBuilder("## Conversation context\n");
    if (hasSummary) {
      sb.append("\n### Session summary\n").append(rollingSummary.trim()).append("\n");
    }
    if (hasMemories) {
      sb.append("\n### Relevant memories\n");
      for (ScoredChunk m : memories) {
        sb.append("- ").append(oneLine(m.content())).append("\n");
      }
    }
    return sb.toString();
  }

  private static String oneLine(String text) {
    if (text == null) return "";
    String collapsed = text.replaceAll("\\s+", " ").trim();
    return collapsed.length() > MAX_MEMORY_CHARS
        ? collapsed.substring(0, MAX_MEMORY_CHARS) + "…"
        : collapsed;
  }
}

package com.dbdeployer.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Maintains a rolling, re-compressed summary of older conversation turns so per-message token cost
 * stops growing linearly (roadmap §3.4/§3.5). The summary is <b>rewritten</b> (not appended), which
 * is what bounds its size.
 *
 * <p>Summarisation should run off the request hot path (after the assistant reply is persisted) and
 * use a small/cheap model, not the chat model. The trigger and prompt construction are pure and
 * unit-testable; {@link #summarise} is the thin model call.
 */
@Service
public class RollingSummaryService {

  static final int SUMMARY_EVERY_N_TURNS = 4; // open-relay cadence
  static final int VERBATIM_TOKEN_BUDGET = 1500;

  static final String SUMMARY_SYSTEM =
      """
      You maintain a running summary of a technical conversation between a developer and an
      infrastructure assistant for "Port Wrangler" (a local Docker/Kafka/LLM management tool).
      Rewrite the PRIOR SUMMARY plus the NEW TURNS into a single updated summary.

      Rules:
      - Preserve every durable fact: instance names, db types, ports, Kafka topic names and
        partition counts, model names/tags, connection strings, and any decision the user made.
      - Preserve unresolved intents ("user still wants to...", "pending: confirm removal of X").
      - Drop pleasantries, restated questions, and anything already acted on and closed.
      - Never invent facts. If a value is unknown, omit it.
      - Output <= 200 tokens, as terse bullet points. No preamble.
      """;

  /**
   * Hybrid trigger (roadmap §3.4): summarise when enough new turns have accrued <b>or</b> the
   * verbatim window has grown past its token budget, whichever fires first.
   */
  public static boolean shouldSummarise(
      int currentSeq, int summarizedThroughSeq, int verbatimWindowTokens) {
    int newTurns = currentSeq - summarizedThroughSeq;
    return newTurns >= SUMMARY_EVERY_N_TURNS || verbatimWindowTokens >= VERBATIM_TOKEN_BUDGET;
  }

  /** Builds the user-side prompt feeding the prior summary + the new turns to be compressed. */
  public static String buildUserPrompt(String priorSummary, String newTurns) {
    String prior = (priorSummary == null || priorSummary.isBlank()) ? "(none)" : priorSummary;
    return "PRIOR SUMMARY:\n" + prior + "\n\nNEW TURNS:\n" + (newTurns == null ? "" : newTurns);
  }

  /** Runs the compression against a (cheap) model and returns the rewritten summary. */
  public String summarise(ChatClient summaryClient, String priorSummary, String newTurns) {
    return summaryClient
        .prompt()
        .system(SUMMARY_SYSTEM)
        .user(buildUserPrompt(priorSummary, newTurns))
        .call()
        .content();
  }
}

package com.dbdeployer.ai;

import com.dbdeployer.model.ChatSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs rolling-summary compression off the request hot path (roadmap §3.4): called after each
 * assistant reply is persisted, checks the hybrid trigger, and — when it fires — rewrites the
 * summary with a small/cheap model via {@link ModelRouter}. Best-effort by design: a summariser
 * failure must never affect the chat.
 */
@Slf4j
@Component
public class RollingSummaryWorker {

  private final ChatSessionService sessions;
  private final RollingSummaryService summaryService;
  private final ModelRouter modelRouter;
  private final String summaryModel;

  public RollingSummaryWorker(
      ChatSessionService sessions,
      RollingSummaryService summaryService,
      ModelRouter modelRouter,
      @Value("${portwrangler.ai.summary-model:llama3.2:3b}") String summaryModel) {
    this.sessions = sessions;
    this.summaryService = summaryService;
    this.modelRouter = modelRouter;
    this.summaryModel = summaryModel;
  }

  /** Fire-and-forget: compress the session's older turns if the hybrid trigger fires. */
  @Async
  public void summariseIfNeeded(ChatSession session) {
    try {
      int verbatimTokens = sessions.unsummarisedTokens(session);
      if (!RollingSummaryService.shouldSummarise(
          session.getCurrentSeq(), session.getSummarizedThroughSeq(), verbatimTokens)) {
        return;
      }
      String newTurns = sessions.unsummarisedTurnsText(session);
      String summary =
          summaryService.summarise(
              modelRouter.statelessClientFor(null, summaryModel),
              session.getRollingSummary(),
              newTurns);
      if (summary != null && !summary.isBlank()) {
        sessions.applySummary(session.getId(), summary.strip());
        log.info(
            "[rag] rolling summary rewritten for session {} (through turn {})",
            session.getId(),
            session.getCurrentSeq());
      }
    } catch (Exception e) {
      log.debug("[rag] summarisation skipped for {}: {}", session.getId(), e.getMessage());
    }
  }
}

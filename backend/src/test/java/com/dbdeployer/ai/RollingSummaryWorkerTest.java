package com.dbdeployer.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbdeployer.model.ChatSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class RollingSummaryWorkerTest {

  @Mock private ChatSessionService sessions;
  @Mock private RollingSummaryService summaryService;
  @Mock private ModelRouter modelRouter;
  @Mock private ChatClient summaryClient;

  private RollingSummaryWorker worker() {
    return new RollingSummaryWorker(sessions, summaryService, modelRouter, "llama3.2:3b");
  }

  private static ChatSession session(int currentSeq, int summarizedThrough) {
    ChatSession s = new ChatSession();
    s.setId("s1");
    s.setCurrentSeq(currentSeq);
    s.setSummarizedThroughSeq(summarizedThrough);
    return s;
  }

  @Test
  void below_the_trigger_no_summary_runs() {
    ChatSession s = session(2, 0); // 2 new turns < 4, and few tokens
    when(sessions.unsummarisedTokens(s)).thenReturn(100);

    worker().summariseIfNeeded(s);

    verify(summaryService, never()).summarise(any(), any(), any());
  }

  @Test
  void trigger_fires_after_enough_turns_and_applies_the_rewritten_summary() {
    ChatSession s = session(4, 0);
    when(sessions.unsummarisedTokens(s)).thenReturn(100);
    when(sessions.unsummarisedTurnsText(s)).thenReturn("USER: hi\nASSISTANT: hello\n");
    when(modelRouter.statelessClientFor(null, "llama3.2:3b")).thenReturn(summaryClient);
    when(summaryService.summarise(summaryClient, null, "USER: hi\nASSISTANT: hello\n"))
        .thenReturn("- greeted");

    worker().summariseIfNeeded(s);

    verify(sessions).applySummary("s1", "- greeted");
  }

  @Test
  void token_budget_alone_can_fire_the_trigger() {
    ChatSession s = session(1, 0); // only 1 turn, but a huge verbatim window
    when(sessions.unsummarisedTokens(s)).thenReturn(2000);
    when(sessions.unsummarisedTurnsText(s)).thenReturn("USER: …\n");
    when(modelRouter.statelessClientFor(null, "llama3.2:3b")).thenReturn(summaryClient);
    when(summaryService.summarise(any(), any(), any())).thenReturn("- long turn");

    worker().summariseIfNeeded(s);

    verify(sessions).applySummary("s1", "- long turn");
  }

  @Test
  void summariser_failures_are_swallowed() {
    ChatSession s = session(4, 0);
    when(sessions.unsummarisedTokens(s)).thenReturn(100);
    when(sessions.unsummarisedTurnsText(s)).thenReturn("USER: hi\n");
    when(modelRouter.statelessClientFor(any(), any()))
        .thenThrow(new IllegalStateException("no ollama"));

    worker().summariseIfNeeded(s); // must not throw

    verify(sessions, never()).applySummary(any(), any());
  }
}

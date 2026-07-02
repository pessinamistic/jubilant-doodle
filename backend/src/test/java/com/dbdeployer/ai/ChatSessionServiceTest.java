package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbdeployer.model.ChatMessage;
import com.dbdeployer.model.ChatSession;
import com.dbdeployer.store.ChatMessageRepository;
import com.dbdeployer.store.ChatSessionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

  @Mock private ChatSessionRepository sessionRepo;
  @Mock private ChatMessageRepository messageRepo;

  private ChatSessionService service() {
    return new ChatSessionService(sessionRepo, messageRepo, new TokenBudget());
  }

  @Test
  void first_turn_creates_the_session_with_a_title_and_two_messages() {
    when(sessionRepo.findById("s1")).thenReturn(Optional.empty());
    when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ChatSession session =
        service()
            .recordTurn(
                "s1",
                "deploy a redis for me",
                "Deploying…",
                new ModelSelection(null, "llama3.1:8b"));

    assertThat(session.getId()).isEqualTo("s1");
    assertThat(session.getTitle()).isEqualTo("deploy a redis for me");
    assertThat(session.getCurrentSeq()).isEqualTo(1);
    assertThat(session.getModelId()).isEqualTo("llama3.1:8b");

    var captor = ArgumentCaptor.forClass(ChatMessage.class);
    verify(messageRepo, times(2)).save(captor.capture());
    List<ChatMessage> saved = captor.getAllValues();
    assertThat(saved.get(0).getRole()).isEqualTo("USER");
    assertThat(saved.get(0).getSeq()).isEqualTo(1);
    assertThat(saved.get(1).getRole()).isEqualTo("ASSISTANT");
    assertThat(saved.get(1).getSeq()).isEqualTo(2);
  }

  @Test
  void later_turns_advance_the_seq_without_retitling() {
    ChatSession existing = new ChatSession();
    existing.setId("s1");
    existing.setTitle("first question");
    existing.setCurrentSeq(3);
    when(sessionRepo.findById("s1")).thenReturn(Optional.of(existing));
    when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ChatSession session =
        service().recordTurn("s1", "and now?", "Done.", new ModelSelection(null, null));

    assertThat(session.getTitle()).isEqualTo("first question");
    assertThat(session.getCurrentSeq()).isEqualTo(4);

    var captor = ArgumentCaptor.forClass(ChatMessage.class);
    verify(messageRepo, times(2)).save(captor.capture());
    assertThat(captor.getAllValues().get(0).getSeq()).isEqualTo(7); // 2·4−1
    assertThat(captor.getAllValues().get(1).getSeq()).isEqualTo(8); // 2·4
  }

  @Test
  void applySummary_updates_summary_state_and_watermark() {
    ChatSession session = new ChatSession();
    session.setId("s1");
    session.setCurrentSeq(6);
    when(sessionRepo.findById("s1")).thenReturn(Optional.of(session));

    service().applySummary("s1", "- user deployed postgres on 5544");

    assertThat(session.getRollingSummary()).isEqualTo("- user deployed postgres on 5544");
    assertThat(session.getSummarizedThroughSeq()).isEqualTo(6);
    assertThat(session.getSummaryTokenCount()).isGreaterThan(0);
    verify(sessionRepo).save(session);
  }

  @Test
  void rollingSummaryFor_filters_blank_summaries() {
    ChatSession blank = new ChatSession();
    blank.setId("s1");
    blank.setRollingSummary("  ");
    when(sessionRepo.findById("s1")).thenReturn(Optional.of(blank));

    assertThat(service().rollingSummaryFor("s1")).isEmpty();
  }
}

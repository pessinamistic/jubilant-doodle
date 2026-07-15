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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

  @Mock private ChatSessionRepository sessionRepo;
  @Mock private ChatMessageRepository messageRepo;
  @Mock private ChatMemory chatMemory;
  @Mock private ChatMemoryIngestionService memoryIngestion;

  private ChatSessionService service() {
    return new ChatSessionService(
        sessionRepo, messageRepo, new TokenBudget(), chatMemory, memoryIngestion);
  }

  @Test
  void first_turn_creates_the_session_with_a_title_and_two_messages() {
    when(sessionRepo.findById("s1")).thenReturn(Optional.empty());
    when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

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

    // Regression for chat_message_session_id_fkey: the session row must be flushed BEFORE
    // any chat_message insert, otherwise the FK is violated on a session's first turn.
    InOrder inOrder = Mockito.inOrder(sessionRepo, messageRepo);
    inOrder.verify(sessionRepo).saveAndFlush(any());
    inOrder.verify(messageRepo, times(2)).save(any());
  }

  @Test
  void later_turns_advance_the_seq_without_retitling() {
    ChatSession existing = new ChatSession();
    existing.setId("s1");
    existing.setTitle("first question");
    existing.setCurrentSeq(3);
    when(sessionRepo.findById("s1")).thenReturn(Optional.of(existing));
    when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

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
  void deleteSession_removes_messages_session_memory_window_and_vector_docs() {
    when(sessionRepo.existsById("s1")).thenReturn(true);

    assertThat(service().deleteSession("s1")).isTrue();

    InOrder inOrder = Mockito.inOrder(messageRepo, sessionRepo);
    inOrder.verify(messageRepo).deleteBySessionId("s1"); // messages first for the FK
    inOrder.verify(sessionRepo).deleteById("s1");
    verify(chatMemory).clear("s1");
    verify(memoryIngestion).deleteForSession("s1");
  }

  @Test
  void deleteSession_returns_false_for_unknown_sessions() {
    when(sessionRepo.existsById("nope")).thenReturn(false);
    assertThat(service().deleteSession("nope")).isFalse();
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

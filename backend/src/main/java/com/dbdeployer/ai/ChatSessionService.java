package com.dbdeployer.ai;

import com.dbdeployer.model.ChatMessage;
import com.dbdeployer.model.ChatSession;
import com.dbdeployer.store.ChatMessageRepository;
import com.dbdeployer.store.ChatSessionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists chat turns into {@code chat_session}/{@code chat_message} (roadmap 6e). This is the
 * retrievable corpus + rolling-summary state; the Spring AI JDBC chat memory keeps its own verbatim
 * window independently. Message seqs are derived from the turn counter so the unique {@code
 * (session_id, seq)} constraint holds: user = {@code 2·turn−1}, assistant = {@code 2·turn}.
 */
@Slf4j
@Service
public class ChatSessionService {

  private static final int TITLE_MAX = 80;

  private final ChatSessionRepository sessionRepo;
  private final ChatMessageRepository messageRepo;
  private final TokenBudget tokenBudget;
  private final ChatMemory chatMemory;
  private final ChatMemoryIngestionService memoryIngestion;

  public ChatSessionService(
      ChatSessionRepository sessionRepo,
      ChatMessageRepository messageRepo,
      TokenBudget tokenBudget,
      ChatMemory chatMemory,
      ChatMemoryIngestionService memoryIngestion) {
    this.sessionRepo = sessionRepo;
    this.messageRepo = messageRepo;
    this.tokenBudget = tokenBudget;
    this.chatMemory = chatMemory;
    this.memoryIngestion = memoryIngestion;
  }

  /** All sessions, most recently active first. */
  public List<ChatSession> listSessions() {
    return sessionRepo.findAllByRecency();
  }

  /** A session's full persisted transcript in order, or empty if the session is unknown. */
  public Optional<List<ChatMessage>> history(String sessionId) {
    if (!sessionRepo.existsById(sessionId)) return Optional.empty();
    return Optional.of(messageRepo.findBySessionIdOrderBySeqAsc(sessionId));
  }

  /** Renames a session; returns the updated session or empty if unknown. */
  @Transactional
  public Optional<ChatSession> rename(String sessionId, String title) {
    return sessionRepo
        .findById(sessionId)
        .map(
            session -> {
              session.setTitle(titleFrom(title));
              return sessionRepo.save(session);
            });
  }

  /**
   * Deletes a session and everything derived from it: its messages (FK order), the Spring AI
   * verbatim memory window, and its {@code chat_memory} documents in pgvector. Returns false if the
   * session is unknown.
   */
  @Transactional
  public boolean deleteSession(String sessionId) {
    if (!sessionRepo.existsById(sessionId)) return false;
    messageRepo.deleteBySessionId(sessionId);
    sessionRepo.deleteById(sessionId);
    try {
      chatMemory.clear(sessionId);
    } catch (Exception e) {
      log.warn("Spring AI memory clear skipped for {}: {}", sessionId, e.getMessage());
    }
    memoryIngestion.deleteForSession(sessionId);
    log.info("[chat] deleted session {}", sessionId);
    return true;
  }

  /**
   * Records one completed turn (user message + assistant reply), creating the session on first use.
   * Returns the updated session.
   */
  @Transactional
  public ChatSession recordTurn(
      String sessionId, String userMessage, String assistantReply, ModelSelection selection) {
    ChatSession session =
        sessionRepo
            .findById(sessionId)
            .orElseGet(
                () -> {
                  ChatSession created = new ChatSession();
                  created.setId(sessionId);
                  created.setTitle(titleFrom(userMessage));
                  return created;
                });
    if (selection != null && selection.modelId() != null && !selection.modelId().isBlank()) {
      session.setModelId(selection.modelId());
    }

    int turn = session.getCurrentSeq() + 1;
    session.setCurrentSeq(turn);
    // Flush the session row FIRST: chat_message.session_id carries an FK to chat_session but no
    // JPA association, so Hibernate would otherwise insert messages before the new session row
    // and violate chat_message_session_id_fkey on a session's first turn.
    session = sessionRepo.saveAndFlush(session);
    messageRepo.save(message(sessionId, 2 * turn - 1, "USER", userMessage));
    messageRepo.save(message(sessionId, 2 * turn, "ASSISTANT", assistantReply));
    return session;
  }

  /** The current rolling summary for a session, if any. */
  public Optional<String> rollingSummaryFor(String sessionId) {
    return sessionRepo
        .findById(sessionId)
        .map(ChatSession::getRollingSummary)
        .filter(s -> s != null && !s.isBlank());
  }

  /** Estimated token size of the turns not yet folded into the rolling summary. */
  public int unsummarisedTokens(ChatSession session) {
    return messageRepo
        .findBySessionIdAndSeqGreaterThanOrderBySeqAsc(
            session.getId(), 2 * session.getSummarizedThroughSeq())
        .stream()
        .mapToInt(m -> tokenBudget.estimate(m.getContent()))
        .sum();
  }

  /** Role-tagged text of the turns to compress next (those past the summarised watermark). */
  public String unsummarisedTurnsText(ChatSession session) {
    StringBuilder sb = new StringBuilder();
    for (ChatMessage m :
        messageRepo.findBySessionIdAndSeqGreaterThanOrderBySeqAsc(
            session.getId(), 2 * session.getSummarizedThroughSeq())) {
      sb.append(m.getRole()).append(": ").append(m.getContent()).append('\n');
    }
    return sb.toString();
  }

  /** Persists a freshly rewritten summary and advances the watermark to the current turn. */
  @Transactional
  public void applySummary(String sessionId, String summary) {
    sessionRepo
        .findById(sessionId)
        .ifPresent(
            session -> {
              session.setRollingSummary(summary);
              session.setSummaryTokenCount(tokenBudget.estimate(summary));
              session.setSummarizedThroughSeq(session.getCurrentSeq());
              sessionRepo.save(session);
            });
  }

  private ChatMessage message(String sessionId, int seq, String role, String content) {
    ChatMessage m = new ChatMessage();
    m.setId(UUID.randomUUID().toString());
    m.setSessionId(sessionId);
    m.setSeq(seq);
    m.setRole(role);
    m.setContent(content == null ? "" : content);
    m.setTokenCount(tokenBudget.estimate(m.getContent()));
    return m;
  }

  private static String titleFrom(String userMessage) {
    if (userMessage == null || userMessage.isBlank()) return "New conversation";
    String t = userMessage.strip();
    return t.length() <= TITLE_MAX ? t : t.substring(0, TITLE_MAX - 1) + "…";
  }
}

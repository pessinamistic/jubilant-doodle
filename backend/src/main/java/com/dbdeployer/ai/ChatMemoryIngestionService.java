package com.dbdeployer.ai;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

/**
 * Writes completed chat turns into the shared pgvector {@code vector_store} as {@code
 * type=chat_memory} documents, so past conversations become retrievable memories: {@link
 * MemoryRetriever} pulls them back (alongside deployment/log docs) and {@link SmartContextBuilder}
 * injects them ahead of the verbatim window on future turns — including turns in <i>other</i>
 * sessions.
 *
 * <p>One document per turn (user question + assistant answer) keeps Q/A pairs semantically intact
 * for embedding. Metadata carries {@code session_id}/{@code seq} so a session's memories can be
 * dropped when the session is deleted, and {@code last_seen} feeds {@link RecencyFrequencyScorer}.
 */
@Slf4j
@Service
public class ChatMemoryIngestionService {

  public static final String TYPE = "chat_memory";

  private final VectorStore vectorStore;

  public ChatMemoryIngestionService(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
  }

  /** Best-effort: embed one completed turn. Failures must never break the finished stream. */
  public void ingestTurn(String sessionId, int turn, String userMessage, String assistantReply) {
    try {
      vectorStore.add(List.of(turnDocument(sessionId, turn, userMessage, assistantReply)));
      log.debug("[rag] ingested chat memory: session={}, turn={}", sessionId, turn);
    } catch (Exception e) {
      log.warn("[rag] chat-memory ingestion skipped (best-effort): {}", e.getMessage());
    }
  }

  /** Remove every memory belonging to a session (used when the session is deleted). */
  public void deleteForSession(String sessionId) {
    try {
      vectorStore.delete(new FilterExpressionBuilder().eq("session_id", sessionId).build());
    } catch (Exception e) {
      log.warn("[rag] chat-memory delete skipped for session {}: {}", sessionId, e.getMessage());
    }
  }

  /** Pure: builds the Q/A document for one turn. */
  static Document turnDocument(
      String sessionId, int turn, String userMessage, String assistantReply) {
    String content = "USER: " + safe(userMessage) + "\nASSISTANT: " + safe(assistantReply);

    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("type", TYPE);
    meta.put("session_id", sessionId);
    meta.put("seq", turn);
    meta.put("last_seen", Instant.now().toString());
    meta.put("access_count", 0);
    return Document.builder().text(content).metadata(meta).build();
  }

  private static String safe(String s) {
    return s == null ? "" : s.strip();
  }
}

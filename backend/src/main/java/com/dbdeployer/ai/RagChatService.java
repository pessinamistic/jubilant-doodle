package com.dbdeployer.ai;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Streams an assistant reply token-by-token over SSE, keyed by session so the verbatim chat-memory
 * window is applied. On each turn it also assembles a "smart context" block (roadmap §3.1): the
 * rolling session summary plus the top-k retrieved memories from pgvector, injected ahead of the
 * verbatim window as a system message. After the stream completes, the turn is persisted to {@code
 * chat_session}/{@code chat_message} and rolling-summary compression is triggered asynchronously.
 *
 * <p>This is the Option-B grounding path: rather than a {@code QuestionAnswerAdvisor} (which is not
 * on the classpath without the {@code spring-ai-advisors-vector-store} module), retrieval and
 * context assembly are done with our own tested {@link MemoryRetriever} and {@link
 * SmartContextBuilder}. Retrieval and persistence are best-effort — a failure degrades to a plain
 * memory-aware chat rather than breaking the stream.
 */
@Slf4j
@Service
public class RagChatService {

  /** How many memories to ground each turn with. */
  private static final int MEMORY_TOP_K = 4;

  private final ModelRouter modelRouter;
  private final MemoryRetriever memoryRetriever;
  private final SmartContextBuilder smartContextBuilder;
  private final ChatSessionService chatSessions;
  private final RollingSummaryWorker summaryWorker;
  private final ChatMemoryIngestionService memoryIngestion;

  public RagChatService(
      ModelRouter modelRouter,
      MemoryRetriever memoryRetriever,
      SmartContextBuilder smartContextBuilder,
      ChatSessionService chatSessions,
      RollingSummaryWorker summaryWorker,
      ChatMemoryIngestionService memoryIngestion) {
    this.modelRouter = modelRouter;
    this.memoryRetriever = memoryRetriever;
    this.smartContextBuilder = smartContextBuilder;
    this.chatSessions = chatSessions;
    this.summaryWorker = summaryWorker;
    this.memoryIngestion = memoryIngestion;
  }

  public Flux<ServerSentEvent<ChatToken>> stream(
      String sessionId, String userMessage, ModelSelection selection) {
    ChatClient client = modelRouter.clientFor(selection.baseUrl(), selection.modelId());

    String rollingSummary = chatSessions.rollingSummaryFor(sessionId).orElse(null);
    String system = ChatClientConfig.SYSTEM_PROMPT + retrievalContext(userMessage, rollingSummary);

    StringBuilder reply = new StringBuilder();
    Flux<ServerSentEvent<ChatToken>> tokens =
        client
            .prompt()
            .system(system)
            .user(userMessage)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .stream()
            .content()
            .map(
                chunk -> {
                  reply.append(chunk);
                  return ServerSentEvent.builder(new ChatToken(chunk)).event("token").build();
                })
            .doOnComplete(() -> persistTurn(sessionId, userMessage, reply.toString(), selection));

    ServerSentEvent<ChatToken> done =
        ServerSentEvent.<ChatToken>builder().event("done").data(new ChatToken("")).build();

    return tokens.concatWith(Flux.just(done));
  }

  /** Best-effort persistence + async summary compression — never breaks the finished stream. */
  private void persistTurn(
      String sessionId, String userMessage, String assistantReply, ModelSelection selection) {
    try {
      var session = chatSessions.recordTurn(sessionId, userMessage, assistantReply, selection);
      memoryIngestion.ingestTurn(sessionId, session.getCurrentSeq(), userMessage, assistantReply);
      summaryWorker.summariseIfNeeded(session);
    } catch (Exception e) {
      log.warn(
          "Turn persistence failed for session {} (best-effort): {}", sessionId, e.getMessage());
    }
  }

  /**
   * Best-effort retrieval + context assembly. Returns {@code ""} when there is nothing to add (or
   * on any retrieval failure), so the caller can always concatenate it onto {@link
   * ChatClientConfig#SYSTEM_PROMPT} without clobbering the base prompt.
   */
  private String retrievalContext(String userMessage, String rollingSummary) {
    List<ScoredChunk> memories = List.of();
    try {
      memories = memoryRetriever.retrieve(userMessage, null, MEMORY_TOP_K);
    } catch (Exception e) {
      log.debug("Memory retrieval skipped (best-effort): {}", e.getMessage());
    }
    String context = smartContextBuilder.build(rollingSummary, memories);
    return context.isBlank() ? "" : "\n\n" + context;
  }
}

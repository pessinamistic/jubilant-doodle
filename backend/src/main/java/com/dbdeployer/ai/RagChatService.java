package com.dbdeployer.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Streams an assistant reply token-by-token over SSE, keyed by session so the verbatim chat-memory
 * window is applied. In Phase 4 this gains RAG context-assembly advisors; for now it is a clean
 * memory-aware streaming chat.
 */
@Service
public class RagChatService {

  private final ModelRouter modelRouter;

  public RagChatService(ModelRouter modelRouter) {
    this.modelRouter = modelRouter;
  }

  public Flux<ServerSentEvent<ChatToken>> stream(
      String sessionId, String userMessage, ModelSelection selection) {
    ChatClient client = modelRouter.clientFor(selection.baseUrl(), selection.modelId());

    Flux<ServerSentEvent<ChatToken>> tokens =
        client
            .prompt()
            .user(userMessage)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .stream()
            .content()
            .map(chunk -> ServerSentEvent.builder(new ChatToken(chunk)).event("token").build());

    ServerSentEvent<ChatToken> done =
        ServerSentEvent.<ChatToken>builder().event("done").data(new ChatToken("")).build();

    return tokens.concatWith(Flux.just(done));
  }
}

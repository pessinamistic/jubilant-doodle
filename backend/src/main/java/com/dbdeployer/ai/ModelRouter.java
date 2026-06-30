package com.dbdeployer.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds a {@link ChatClient} bound to a chosen Ollama runtime + model per request, enabling
 * dynamic model switching across the several Ollama containers a user may deploy (roadmap §4.2).
 * Each client is configured with the conversation-memory advisor so the verbatim window is applied.
 */
@Service
public class ModelRouter {

  private final ChatMemory chatMemory;
  private final String defaultBaseUrl;
  private final String defaultModel;

  public ModelRouter(
      ChatMemory chatMemory,
      @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String defaultBaseUrl,
      @Value("${portwrangler.ai.default-model:llama3.1:8b}") String defaultModel) {
    this.chatMemory = chatMemory;
    this.defaultBaseUrl = defaultBaseUrl;
    this.defaultModel = defaultModel;
  }

  /** A client bound to a specific runtime base URL + model, with conversation memory enabled. */
  public ChatClient clientFor(String baseUrl, String modelId) {
    return ChatClient.builder(chatModelFor(baseUrl, modelId))
        .defaultSystem(ChatClientConfig.SYSTEM_PROMPT)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();
  }

  /** A client bound to a plain model (no memory advisor) — used for stateless comparisons. */
  public ChatClient statelessClientFor(String baseUrl, String modelId) {
    return ChatClient.builder(chatModelFor(baseUrl, modelId))
        .defaultSystem(ChatClientConfig.SYSTEM_PROMPT)
        .build();
  }

  /**
   * The raw {@link ChatModel} bound to a chosen runtime + model (an {@link OllamaChatModel} under
   * the hood). The agentic tool loop drives this directly (manual {@code call(Prompt)} + {@code
   * ToolCallingManager}) rather than through a {@link ChatClient}, so it can gate confirmation
   * between rounds (roadmap §5).
   */
  public ChatModel chatModelFor(String baseUrl, String modelId) {
    String url = (baseUrl == null || baseUrl.isBlank()) ? defaultBaseUrl : baseUrl;
    String model = (modelId == null || modelId.isBlank()) ? defaultModel : modelId;

    OllamaApi api = OllamaApi.builder().baseUrl(url).build();
    return OllamaChatModel.builder()
        .ollamaApi(api)
        .defaultOptions(OllamaOptions.builder().model(model).build())
        .build();
  }
}

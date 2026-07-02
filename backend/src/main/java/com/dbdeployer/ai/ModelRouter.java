package com.dbdeployer.ai;

import com.dbdeployer.runtime.ModelDashboardService;
import com.dbdeployer.runtime.ModelRuntimeService;
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
 *
 * <p>When the caller passes no base URL, the {@link ModelRuntimeService} registry resolves it — a
 * RUNNING managed Ollama instance wins over the static {@code spring.ai.ollama.base-url} default.
 */
@Service
public class ModelRouter {

  private final ChatMemory chatMemory;
  private final ModelRuntimeService modelRuntimes;
  private final ModelDashboardService modelDashboard;
  private final String defaultModel;

  public ModelRouter(
      ChatMemory chatMemory,
      ModelRuntimeService modelRuntimes,
      ModelDashboardService modelDashboard,
      @Value("${portwrangler.ai.default-model:llama3.1:8b}") String defaultModel) {
    this.chatMemory = chatMemory;
    this.modelRuntimes = modelRuntimes;
    this.modelDashboard = modelDashboard;
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
    String url =
        (baseUrl == null || baseUrl.isBlank()) ? modelRuntimes.resolveOllamaBaseUrl() : baseUrl;
    String model = (modelId == null || modelId.isBlank()) ? defaultModel : modelId;

    OllamaApi api = OllamaApi.builder().baseUrl(url).build();
    return OllamaChatModel.builder().ollamaApi(api).defaultOptions(optionsFor(model)).build();
  }

  /**
   * Options for a model, honouring the per-model settings saved in the runtime dashboard
   * (temperature / context window / keep-alive). Unset fields keep the runtime defaults.
   */
  private OllamaOptions optionsFor(String model) {
    OllamaOptions.Builder options = OllamaOptions.builder().model(model);
    modelDashboard
        .settingsFor(model)
        .ifPresent(
            s -> {
              if (s.temperature() != null) options.temperature(s.temperature());
              if (s.numCtx() != null) options.numCtx(s.numCtx());
              if (s.keepAlive() != null && !s.keepAlive().isBlank()) {
                options.keepAlive(s.keepAlive());
              }
            });
    return options.build();
  }
}

Great project! I read through the whole repo — Port Wrangler is very well-structured. The PipelineOrchestrator, DockerDeployEngine, and DatabaseCatalog patterns are a perfect foundation to build the AI manager on. Here's the full architecture for the new module:
::view-transition-group(_),
::view-transition-old(_),
::view-transition-new(\*) {
animation-duration: 0.25s;
animation-timing-function: cubic-bezier(0.19, 1, 0.22, 1);
}
VvisualizeVvisualize show_widgetThe core idea is that Ollama fits naturally as another managed container — you're already pulling images, running containers, and managing pipelines for databases. The AI module reuses all of that and layers Spring AI on top for model management.

How the pieces fit together
Ollama is the local LLM runtime — it runs as a Docker container (just like your Kafka or Redis) and exposes a REST API on port 11434. Spring AI's Ollama adapter sits on top of that API, giving you Java objects for chat, embeddings, and model management.
The key insight: you don't need Spring AI for the deployment lifecycle (pull image → create container → start). Your DockerDeployEngine and PipelineOrchestrator already handle that perfectly. Spring AI comes in for the AI-specific operations: pulling models into Ollama, listing them, checking their metadata, and doing inference for the quick-test chat.

Gradle dependency changes
In backend/build.gradle.kts, add the Spring AI BOM and Ollama starter:
kotlinextra["springAiVersion"] = "1.0.0"

dependencies {
// Spring AI
implementation(platform("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}"))
implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")
}
And in application.yml, add a conditional Ollama config block that points to wherever your managed Ollama container is running:
yamlspring:
ai:
ollama:
base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
chat:
options:
model: ${OLLAMA_DEFAULT_MODEL:llama3.2:3b}

portwrangler:
ai:
ollama-container-name: portwrangler-ollama
ollama-host-port: 11434
ollama-image: ollama/ollama

New backend components
AiModelCatalog.java — mirrors your existing DatabaseCatalog. A static registry of the 15+ models you support, with metadata like parameter size, context window, use case tags, and recommended RAM:
javapublic enum AiModelFamily { LLAMA, MISTRAL, GEMMA, PHI, QWEN, DEEPSEEK, NOMIC }

public record ModelDefinition(
String pullTag, // e.g. "llama3.2:3b"
String displayName, // "Llama 3.2 3B"
AiModelFamily family,
String parameterSize, // "3B"
String quantization, // "Q4_K_M"
int contextWindowK, // 128 (meaning 128k tokens)
int minRamGb,
String[] useCaseTags, // ["chat", "code", "rag"]
String description
) {}
Key models to include: Llama 3.2 (1B, 3B, 11B), Llama 3.1 (8B, 70B), Mistral 7B, Mistral-Nemo, Phi-4 (14B), Gemma 3 (1B, 4B, 12B), Qwen2.5 (3B, 7B, 14B), DeepSeek-R1 (1.5B, 7B), Nomic Embed Text (for embeddings), and all-minilm.
AiModelInstance.java — a new JPA entity stored in your existing system PostgreSQL alongside DeploymentConfig:
java@Entity
@Table(name = "ai_model_instances")
public class AiModelInstance {
@Id @GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
private String pullTag; // "llama3.2:3b"
private String displayName;
@Enumerated(EnumType.STRING)
private AiModelStatus status; // PULLING, READY, FAILED, DELETED
private Long sizeBytes;
private String digest;
private String quantization;
private LocalDateTime pulledAt;
private LocalDateTime lastUsedAt;
}
OllamaApiClient.java — thin wrapper around Spring AI's OllamaApi for the operations the UI needs:
java@Service
public class OllamaApiClient {
private final OllamaApi ollamaApi;

    // List models currently pulled in Ollama
    public List<OllamaApi.Model> listLocalModels() {
        return ollamaApi.listModels().models();
    }

    // Async model pull (long-running) — fires and returns; status polled separately
    public void pullModel(String modelTag) {
        ollamaApi.pullModel(new OllamaApi.PullModelRequest(modelTag, false));
    }

    // Delete a model from Ollama's local store
    public void deleteModel(String modelTag) {
        ollamaApi.deleteModel(new OllamaApi.DeleteModelRequest(modelTag));
    }

    // Get model info (parameters, template, license, context window)
    public OllamaApi.ModelInfo showModelInfo(String modelTag) {
        return ollamaApi.showModelInfo(new OllamaApi.ShowModelInfoRequest(modelTag));
    }

    // Quick single-turn chat for the UI test panel
    public String quickChat(String modelTag, String userMessage) {
        ChatResponse response = new OllamaChatModel(ollamaApi,
            OllamaOptions.builder().model(modelTag).build())
            .call(new Prompt(userMessage));
        return response.getResult().getOutput().getText();
    }

}
SpringAiConfigExporter.java — generates ready-to-paste Spring AI configuration snippets that developers can copy into their own apps:
java@Service
public class SpringAiConfigExporter {

    public String generateYamlConfig(String modelTag) {
        return """
            spring:
              ai:
                ollama:
                  base-url: http://localhost:11434
                  chat:
                    options:
                      model: %s
            """.formatted(modelTag);
    }

    public String generateJavaBean(String modelTag) {
        return """
            @Bean
            OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
                return new OllamaChatModel(ollamaApi,
                    OllamaOptions.builder()
                        .model("%s")
                        .temperature(0.7)
                        .build());
            }
            """.formatted(modelTag);
    }

}

New REST API endpoints
Under /api/ai/:
GET /api/ai/ollama Ollama service status + container info
POST /api/ai/ollama/deploy Deploy Ollama as a managed Docker container
POST /api/ai/ollama/start Start existing Ollama container
POST /api/ai/ollama/stop Stop Ollama container

GET /api/ai/catalog All models available to pull (from AiModelCatalog)
GET /api/ai/models Models currently tracked (your DB + Ollama sync)
POST /api/ai/models/pull Pull a model — kicks off existing pipeline system
DELETE /api/ai/models/{tag} Delete model from Ollama + your DB
GET /api/ai/models/{tag}/info Detailed model metadata (Spring AI ShowModelInfo)
GET /api/ai/models/{tag}/metrics Runtime stats (size, load time, last used)
POST /api/ai/models/{tag}/chat Quick test chat (streamed SSE)

GET /api/ai/config/{tag}/yaml Spring AI YAML config snippet
GET /api/ai/config/{tag}/java Spring AI Java @Bean snippet
The model pull integrates directly with PipelineOrchestrator — it creates a pipeline with steps like PULL_OLLAMA_IMAGE → START_OLLAMA_CONTAINER → PULL_MODEL → VERIFY_MODEL, reusing all the existing pipeline persistence, status tracking, and recovery logic.

Frontend: AiModelsPage.jsx
The page follows the same left-nav + right-content layout as DeployPage.jsx. Three main panels:
Left nav — Ollama service card showing: running/stopped status, container uptime, total models pulled, current RAM usage. A Deploy/Start/Stop button depending on state.
Center: Model catalog — a grid of ModelCard components, each showing the model name, parameter size, minimum RAM badge, use-case tags (chat, code, RAG, embeddings), and a Pull button with a progress indicator that polls /api/ai/models/pull pipeline status (same pattern as your existing DeployPage pipeline polling).
Right: Model details drawer — opens when you click a pulled model. Shows: model digest + quantization, context window size, a quick-test chat input, and the Spring AI integration snippet with a copy button. This is the "easy integration management" you mentioned — devs can grab the config right from the UI.

Key design decisions to consider
Ollama as a managed container vs. assumed pre-existing. You could either treat Ollama exactly like PostgreSQL (deploy via Port Wrangler, fully managed) or assume users have Ollama installed natively and just point Spring AI at localhost:11434. The managed container approach fits your existing UX better and makes the tool fully self-contained.
Model pull as an async pipeline. Model pulls can take minutes for large models (70B is ~40GB). Using your existing PipelineOrchestrator and SSE polling gives you progress tracking for free.
Metrics source. Ollama doesn't expose Prometheus metrics natively, but it does return model info via the /api/show endpoint and you can track invocation counts and last-used timestamps in your own DB. For RAM usage, you can call the Docker SDK's statsCmd on the Ollama container — the same way you'd monitor any container.
Spring AI config exporter. This is the feature that makes Port Wrangler genuinely useful beyond just running containers. Once someone has a model running, they get a one-click copy of exactly the Spring AI config they need to use it in their own app — this is the "easy integration management" core value.
Want me to scaffold any of these files in full, or dig into a specific part like the pull pipeline, the metrics panel, or the frontend model catalog component?

Technical Research: Local LLM AI Model Management with Spring AI
1. Executive Overview
   Your existing Port Wrangler architecture, built around a PipelineOrchestrator, DockerDeployEngine, and DatabaseCatalog, provides a robust and battle-tested foundation. The integration of a local LLM AI model manager is a natural and powerful extension. This research proposes treating an LLM runtime, such as Ollama, as a managed Docker container, similar to your existing managed databases (PostgreSQL, Kafka, etc.). The core differentiation lies in the post-deployment layer, where Spring AI will be used to manage the models within the runtime, providing unified APIs for chat, embedding, and model lifecycle management, while Spring Boot Actuator will expose detailed metrics for a new AI-focused UI.

2. Recommended Architecture
   The new module will sit alongside your existing services, leveraging the same patterns for container management while introducing new components for AI-specific operations.

flowchart LR
subgraph "User & UI Layer"
A[React Frontend (New AI Pages)]
end

    subgraph "Port Wrangler Backend (Spring Boot)"
        B[Existing REST Controllers (/api/instances)]
        C[New AI REST Controllers (/api/ai/*)]
        D[Existing PipelineOrchestrator]
        E[Spring AI Services (OllamaChatModel)]
        F[Actuator / Micrometer (Metrics)]
    end

    subgraph "Docker Runtime"
        G[Existing DB Containers (Postgres, Kafka, etc.)]
        H[Ollama Container (Managed by Port Wrangler)]
        I[Other Potential Runtimes (vLLM, llama.cpp)]
    end

    subgraph "Persistence & Monitoring"
        J[(System PostgreSQL DB)]
        K[Prometheus / Grafana (Optional)]
    end

    A -- HTTP --> C
    A -- HTTP --> B
    C -- uses --> D
    C -- uses --> E
    E -- HTTP API --> H
    D -- Docker Java SDK --> H
    F -- scrapes --> K
    K -- visualizes in --> A
This architecture leverages your existing strength: treating Ollama as a "first-class" managed Docker container using your proven deployment pipeline. Once running, Spring AI takes over for model-level operations, and Spring Boot Actuator provides rich, out-of-the-box metrics for the UI.

3. LLM Runtime Engine Selection
   Choosing the right inference engine is critical. The table below synthesizes findings from the provided reports and current 2026 research.

Runtime	Ease of Integration	Containerization	GPU Support	Resource Footprint	2026 Strengths / Use Cases
Ollama	Easiest (OpenAI-compatible API, best Spring AI support)	Excellent (Official Docker image)	Good (via NVIDIA Container Toolkit, 10-100x speedup on GPU)	Low (Uses llama.cpp quantized models)	Developer Experience & Versatility: The go-to for local development and a wide range of tasks. Its tight integration with Spring AI makes it the ideal default engine.
llama.cpp Server	Moderate (Requires custom HTTP client or uses OpenAI wrapper)	Excellent (Official & community Docker images)	Good (NVIDIA CUDA, explicit GPU layer control)	Lowest	Bare-Metal Speed & Control: Offers the fastest raw inference performance and unparalleled flexibility over model parameters (layer splitting, context, etc.).
vLLM	Moderate (OpenAI-compatible API, requires Python environment)	Good (Official Docker image)	Excellent (NVIDIA, AMD, Intel, Apple. GPU-first design)	High (Designed for full-precision, high-throughput models)	High-Concurrency Production: The best choice for serving multiple concurrent requests. Handles 10+ requests at 630 tok/s vs. sequential Ollama (~83 tok/s).
Recommendation: Implement Ollama as the primary engine for its superior Spring AI integration and developer-friendly nature. Use a plugin architecture to support llama.cpp and vLLM as optional, user-selectable runtimes for advanced use cases requiring maximum performance or concurrency.

4. Spring AI Integration Deep Dive
   Spring AI is your bridge to a uniform model management API.

4.1. Core Dependency
Add the Spring AI BOM and Ollama starter to your backend/build.gradle.kts.

kotlin
extra["springAiVersion"] = "1.0.0-M5"

dependencies {
// Existing dependencies...

    // Spring AI
    implementation(platform("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}"))
    implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")
    
    // Actuator for metrics
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}
4.2. Configuration
The application.yml is where you tie Spring AI to your managed container. The OllamaConnectionDetails can be auto-configured by using a container named ollama/ollama, or you can point it to the host port. This integration allows Spring AI's ChatClient and OllamaChatModel to seamlessly communicate with the Ollama API for chat completions and model management.

yaml
spring:
ai:
ollama:
chat:
options:
model: ${OLLAMA_DEFAULT_MODEL:llama3.2:3b}
temperature: 0.7

# Actuator exposes metrics on /actuator/prometheus
management:
endpoints:
web:
exposure:
include: "health,info,metrics,prometheus"
metrics:
export:
prometheus:
enabled: true
4.3. Observability & Metrics
Spring AI provides deep observability out of the box. Its ChatClient, ChatModel, and Advisors are instrumented with Micrometer, automatically collecting crucial metrics like token counts, response latency, and costs. By enabling the /actuator/prometheus endpoint, you can scrape these metrics and build rich visualizations in your React UI or in tools like Grafana.

5. Docker Container Management & GPU Support
   This area leverages your existing codebase most directly.

5.1. Managed Deployment
Reuse your DatabaseCatalog pattern and PipelineOrchestrator. An AiModelCatalog will define the available runtimes.

java
// Pseudo-code for orchestrating an Ollama container deployment
DeploymentConfig ollamaConfig = new DeploymentConfig();
ollamaConfig.setDbType("OLLAMA"); // Reuse the concept, or create a new type
ollamaConfig.setImage("ollama/ollama:latest");
ollamaConfig.setHostPort(11434);
ollamaConfig.setContainerPort(11434);
ollamaConfig.setVolumes(List.of(new Mount().withType("volume").withSource("ollama_data").withTarget("/root/.ollama")));
// ... Add to queue for PipelineOrchestrator
5.2. GPU Acceleration
This is a key feature. You can integrate support for NVIDIA GPUs by leveraging the NVIDIA Container Toolkit and using your DockerDeployEngine to pass the appropriate parameters. For a docker run command, this would be --gpus all. Using the Docker Java SDK, you can configure a HostConfig to request GPU resources, enabling significant inference speedups.

5.3. Persistent Model Storage
Without a persistent volume, pulling large models (20–70GB) on every restart would be disastrous. You must configure Docker volumes to persist the Ollama cache (typically ~/.ollama/models inside the container) to avoid this. This can be done with a Docker Compose volume mapping, and Port Wrangler can provision and manage this volume on first start.

6. Security & Isolation Best Practices
   Running LLM containers introduces new security considerations, as a compromised model could attempt to break out.

Practice	Implementation	Rationale
Drop All Linux Capabilities	Use --cap-drop=ALL in docker run or cap_drop: ALL in docker-compose.yml.	Prevents a container from performing privileged operations, even if it is compromised.
Prevent Privilege Escalation	Use --security-opt=no-new-privileges.	Ensures the container's processes cannot gain new, elevated privileges.
Restrict Resource Usage	Set --memory, --cpus, and --gpus limits.	Prevents a single model from consuming all available system resources.
Read-Only Root Filesystem	Use --read-only and mount specific directories as writable.	Enhances security by preventing the container from writing to its own filesystem.
Avoid Mounting the Docker Socket	Do not mount /var/run/docker.sock into LLM containers.	This is a critical security risk; a compromised container could control the host's Docker daemon.
7. UI Design Patterns & Frontend Integration
   The UI should replicate the successful patterns from your existing DeployPage.jsx.

7.1. New AI Management Page (AiModelsPage.jsx)
Left Nav (Runtimes): A list of managed LLM runtimes (e.g., "Ollama", "vLLM"). Selecting one shows its status (Deployed, Stopped, etc.).

Top Bar: A "Deploy New Runtime" button that opens a form to select the engine (Ollama, llama.cpp, vLLM) and configure resources (GPU, memory, CPU).

Center (Model Catalog): A grid of cards, one for each model supported by the catalog (e.g., Llama 3.2 3B, Mistral 7B). Each card shows metadata (parameters, size) and a "Pull" button. This button triggers the async pipeline and shows a progress bar.

Right Drawer (Model Details): Opens on model selection. Contains:

Model Details: Digest, quantization, context window.

Quick-Test Chat: A simple chat interface for immediate feedback.

Integration Snippet: One-click copy-paste of the exact application.yml configuration needed for a developer to use this model.

Metrics Panel: Real-time charts from the Spring AI Micrometer data, showing tokens-per-second, latency, and GPU memory usage.

This UI empowers developers to not only manage models but also seamlessly integrate them into their own Spring Boot applications, fulfilling your goal of "easy integration management."

8. Implementation Roadmap
   A phased approach will deliver value iteratively and safely.

Phase 1: Foundation (Weeks 1-2)
Backend: Add Spring AI dependencies and configuration.

Backend: Create AiModelCatalog, AiModelInstance JPA entities.

Backend: Extend DockerDeployEngine to deploy an Ollama container.

Backend: Implement a simple OllamaApiClient to pull and list models.

Frontend: Create a basic AiModelsPage.jsx that can list/start/stop the Ollama container.

Phase 2: Model Lifecycle & Metrics (Weeks 3-4)
Backend: Integrate model pulling with your PipelineOrchestrator for async progress tracking.

Backend: Enable spring-boot-starter-actuator and expose /actuator/prometheus.

Backend: Create REST endpoints for model details and quick chat.

Frontend: Build the model catalog grid, pull button with progress, and quick-test chat drawer.

Phase 3: Advanced Features & Polish (Weeks 5-6)
Backend: Implement the "Integration Snippet" feature (/api/ai/config/{tag}).

Backend: Add support for optional runtimes (llama.cpp, vLLM). Add GPU detection and configuration to the UI.

Frontend: Build the metrics dashboard panel, fetching data from the Actuator endpoint. Add Docker volume management for persistent storage.

By following this research-backed plan, you can transform Port Wrangler from a mere database manager into a comprehensive local development platform for the AI era, providing immense value to developers who want to build with LLMs without leaving their local environment.


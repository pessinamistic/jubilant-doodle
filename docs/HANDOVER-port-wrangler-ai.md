# Port Wrangler AI — Agent Handover

> **Purpose:** resume the Port Wrangler AI build exactly where the previous agent left off.
> **Read order:** this doc → `CONTEXT.md` → `docs/port-wrangler-roadmap-grounded.md` (source of truth).
> **Last updated:** session ending on branch `phase/4-advisors`.

---

## 1. TL;DR — where we are

The 6-phase roadmap to turn Port Wrangler into an AI-agent infra cockpit is **mostly done**.

| Phase | Status | Branch |
|---|---|---|
| 0 — Hardening | ✅ done (was already on `master`) | `master` |
| 1 — Quick wins (Compose export, Spring config) | ✅ merged | `phase/1-quick-wins` |
| 2 — LLM Runtime + Model Cookbook | ✅ merged | `phase/2-llm-runtime` |
| 3 — Spring AI foundation + Model Comparison | ✅ merged | `phase/3-spring-ai` |
| 4 — RAG foundation | ✅ core merged; advisor wiring in progress | `phase/4-rag` (merged), `phase/4-advisors` (current) |
| 5 — Agentic tools + MCP server | ✅ core merged; confirmation loop + FE pending | `phase/5-agent` |

**Integration branch:** `feature/port-wrangler-ai` (everything merges here; single PR into `master` at the very end). `master` is `7563946`, **untouched** — never rewrite it.

**Current working branch:** `phase/4-advisors` (one commit ahead of `feature`: `SmartContextBuilder`).

Everything on `feature/port-wrangler-ai` passes `./gradlew clean spotlessCheck test` (24 test classes) and both `npm run build` (frontend) runs clean.

---

## 2. Conventions (follow exactly)

- **Commits:** one per logical unit, message prefix `feat(phaseN): …`. **DO NOT add a `Co-authored-by` trailer** (the user explicitly disabled it; earlier commits were rewritten to remove it).
- **Branching:** create each phase tail branch from the tip of `feature/port-wrangler-ai`; merge back with `git merge --no-ff`. Never branch from another phase branch. Never merge into `master`.
- **Only stage your own files.** There are pre-existing unrelated dirty files in the tree — **do not commit them**: `.gitignore`, `backend/.../ImageTrackingStatus.java`, `backend/.../ImageValidationService.java`, and untracked `CONTEXT.md`, `backend/AGENTS.md`, `backend/CLAUDE.md`, `opencode.json`, `graphify-out/`, `docs/prompt`, `frontend/jsconfig.json`.
- **Schema:** Liquibase only, `db/changelog/changes/V<N>__<snake>.sql`, `--changeset portWrangler:<N> labels:phase<N>`. Registered via `includeAll` (just drop the file in). Never Flyway, never `ddl-auto`.
- **Java package stays `com.dbdeployer`.** Frontend is plain JSX (no TS), tabs in some files — match the file you're editing.
- **Verify-before-build:** any new dependency, confirm it resolves (`./gradlew dependencies --configuration runtimeClasspath | grep <artifact>`) before writing code against it.
- **Test pattern:** controllers → `MockMvcBuilders.standaloneSetup(controller)` (no Spring context); services → Mockito mocks; Spring AI → stub `ChatModel` (see `RagChatServiceTest`). Pure algorithm cores are extracted specifically so they're unit-testable without live infra.

---

## 3. Hard constraints (non-negotiable)

| Constraint | Correct |
|---|---|
| Spring AI version | **`1.0.3` GA** (BOM pins it) — never a milestone/RC/1.1.x |
| Vector store | pgvector on the existing Postgres (no Chroma/Pinecone) |
| Web stack | servlet (`spring-boot-starter-web`) — **no WebFlux/R2DBC**. SSE returns `Flux<ServerSentEvent>` from MVC. |
| LLM runtime | Ollama default |
| Schema | Liquibase changeset |
| GPU detect | Docker SDK + `/dev` probes — **never** shell `nvidia-smi` |
| pgvector index | HNSW (not IVFFlat), cosine, 768 dims (fixed) |

---

## 4. What exists now (inventory)

### Backend `com.dbdeployer`
- **`ai/`** — `ChatClientConfig` (ChatMemory window=8), `ModelRouter` (per-request Ollama `ChatClient`, `clientFor` w/ memory advisor + `statelessClientFor`), `RagChatService` (SSE token stream), `ModelComparisonService` (Flux.merge), `ModelSelection`/`ChatToken`/`ComparisonChunk` records.
- **`ai/` RAG** — `RecencyFrequencyScorer` (0.35·sem+0.35·rec+0.30·freq), `TokenBudget` (chars/4), `LogChunker` (severity-tagged record grouping), `IngestionService` (deployment+log docs → `VectorStore`), `MemoryRetriever` (over-fetch + composite rerank), `RollingSummaryService` (hybrid trigger + rewrite prompt), `ScoredChunk`, **`SmartContextBuilder`** (layered context block — newest).
- **`ai/tools/`** — `InfrastructureTools` (`@Tool` methods wrapping `DbInstanceService`: read-only `listInstances/readLogs/connectionConfig/stackSummary`, destructive `stopInstance/removeInstance`), `AgentSafety` (`MAX_TOOL_ROUNDS=6`, DESTRUCTIVE/WRITE sets, `requiresConfirmation`), `InstanceSummary`/`StackSummary`.
- **`mcp/`** — `McpServerConfig` (`ToolCallbackProvider` from `MethodToolCallbackProvider`; `filterReadOnly` strips destructive unless `portwrangler.mcp.write-enabled=true`, shares `AgentSafety.DESTRUCTIVE`).
- **`runtime/`** — `ModelRuntime`, `ModelCatalog` (~25 models), `GpuDetector` (pure `classify`, **public**), `ModelSuggestionService`, `SystemProfile`, `GpuVendor`/`ModelType`/`Quantization`/`CompatibilityLevel`, `ModelDefinition`/`ModelSuggestion`, `OllamaModelPuller`.
- **`deploy/`** — `GpuHostConfigurer` (NVIDIA DeviceRequest / AMD devices), `DockerDeployEngine` now has `resolveEnv()`, `dockerRuntimeNames()`, `buildHostConfig()` (GPU for ollama/model-runner images), single ctor `DockerDeployEngine(GpuHostConfigurer)`. `ConnectionStringBuilder.springBootProperties()`.
- **`pipeline/step/`** — `ModelPullStep` (`StepType.PULL_MODEL`).
- **`api/`** — `ComposeExportController` (`/export/docker-compose`), `ModelCookbookController` (`/models/profile`, `/models/suggestions`), `ChatController` (`/chat/stream` SSE), `ModelComparisonController` (`/models/compare` SSE), plus `DbInstanceController.springConfig` (`/instances/{id}/spring-config`).

### Migrations
`V5__baseline` (drift) · `V6__add_model_runtime` · `V7__add_chat_memory` (SPRING_AI_CHAT_MEMORY + chat_session + chat_message) · `V8__enable_pgvector` (vector_store, HNSW, GIN).

### Frontend `frontend/src`
- Pages: `ModelCookbookPage` (`/models`), `ChatPage` (`/chat`, EventSource), `ComparePage` (`/compare`, EventSource).
- `api/client.js`: `exportDockerCompose`, `getSpringConfig`, `getSystemProfile`, `getModelSuggestions`.
- `App.jsx` routes + `AppShell.jsx` nav (desktop + mobile) updated for Models/Assistant.
- Phase-1 buttons on `InstancesPage` (Export Compose) and `InstanceDetailPage` (Copy Spring config).

### Config / files
- `build.gradle.kts`: Spring AI BOM 1.0.3 + starters `model-ollama`, `model-chat-memory-repository-jdbc`, `vector-store-pgvector`, `mcp-server`.
- `application.yml`: `spring.ai.ollama` (lazy init), `chat.memory…initialize-schema: never`, `vectorstore.pgvector` (initialize-schema false, 768/HNSW/cosine), `mcp.server` name/version; `portwrangler.agent.read-only` + `portwrangler.mcp.write-enabled` flags.
- `.mcp/port-wrangler.json` (Cursor/Claude Desktop client config).

---

## 5. ⚠️ Immediate next step (you are mid-task here)

**Goal of `phase/4-advisors`:** wire RAG retrieval + the smart-context block into the live chat path.

**Blocker just hit:** `QuestionAnswerAdvisor` is **NOT on the classpath**. The pgvector starter pulls `spring-ai-pgvector-store` but **not** the `spring-ai-advisors-vector-store` module. A first attempt at `InfraRagAdvisorConfig` failed to compile and **was deleted** (do not look for it).

**Available advisor classes** (verified in jars): `BaseAdvisor`, `BaseChatMemoryAdvisor`, `MessageChatMemoryAdvisor`, `PromptChatMemoryAdvisor`, `SafeGuardAdvisor`, `SimpleLoggerAdvisor`. **No** `QuestionAnswerAdvisor`/`RetrievalAugmentationAdvisor`.

**Two ways forward — prefer Option B:**

- **Option A:** add `implementation("org.springframework.ai:spring-ai-advisors-vector-store")` to `build.gradle.kts`, confirm it resolves, then build `QuestionAnswerAdvisor.builder(vectorStore).searchRequest(SearchRequest.builder().topK(4).build()).build()` and attach via `.advisors(...)`.
- **Option B (recommended — no new dep, uses our tested code):** do RAG grounding through the pieces we already built. In `RagChatService.stream(...)`:
  1. `var mems = memoryRetriever.retrieve(userMessage, /*type*/ null, 4);` (KB grounding) — wrap in try/catch, best-effort.
  2. `String context = smartContextBuilder.build(rollingSummary, mems);` (rollingSummary can be `null` until `chat_session` persistence exists).
  3. Inject as the system message **without clobbering the base prompt**:
     `String system = ChatClientConfig.SYSTEM_PROMPT + (context.isBlank() ? "" : "\n\n" + context);`
     then `client.prompt().system(system).user(userMessage)…`.
     (⚠️ `.system(text)` **overrides** the default system from `ModelRouter`, so always concatenate `SYSTEM_PROMPT + context` yourself.)
  4. Keep `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))` for the memory window.

**When wiring `RagChatService`,** its constructor will gain `MemoryRetriever` + `SmartContextBuilder`. Update `RagChatServiceTest` to pass a **mocked `MemoryRetriever`** (`retrieve(...)` → `List.of()`) and a real `SmartContextBuilder`; the existing token+done assertions still hold (empty context ⇒ unchanged behaviour).

Then: `./gradlew spotlessApply spotlessCheck test`, commit `feat(phase4): wire RAG context into chat path`, and **merge `phase/4-advisors` into `feature/port-wrangler-ai`** (`--no-ff`).

> The roadmap's `ContextAssemblyAdvisor`/`InfraRagAdvisor` as formal `BaseAdvisor`s (order 100/200) are optional niceties; Option B achieves the same layered prompt (roadmap §3.1) with less API risk. If you want the formal advisor later, `BaseAdvisor` is available — implement `before(ChatClientRequest, AdvisorChain)`/`after(...)`; expect to iterate the immutable `ChatClientRequest.mutate()` API.

---

## 6. Remaining work after that (priority order)

### 6a. CI verification — **HIGH, do early**
The Spring AI + MCP autoconfig is now on the classpath, so the existing `@SpringBootTest` (`DeployPipelineIT`, `disabledWithoutDocker`) now boots Ollama + chat-memory + pgvector + MCP autoconfig. **It was never run this session (no Docker in sandbox).** Run it with Docker/CI and confirm context startup is green. Mitigations already in `application.yml` (lazy Ollama, `initialize-schema` off, explicit pgvector dims). If it fails, nudge `spring.ai.*` properties or add `@MockBean`/exclusions in the test.

### 6b. Phase 5 — confirmation-gated tool execution (MEDIUM)
Implement the agent chat that actually calls tools with the manual confirmation loop (roadmap §5):
- New `ai/AgentChatService` using `ToolCallingChatOptions.builder().toolCallbacks(ToolCallbacks.from(infrastructureTools)).internalToolExecutionEnabled(false).build()`, a `ToolCallingManager` bean (add to `ChatClientConfig`), and a `while (resp.hasToolCalls())` loop capped at `AgentSafety.MAX_TOOL_ROUNDS`.
- Read-only tools auto-run; `AgentSafety.requiresConfirmation(name)` → emit a `confirmation_request` SSE event and await approval before `toolCallingManager.executeToolCalls(...)`.
- `portwrangler.agent.read-only=true` ⇒ build the tool list with destructive/write tools stripped (reuse `AgentSafety`).
- Stream each tool call as a `tool_call` SSE event.
- Endpoint e.g. `GET /chat/agent/stream`. Testable: stub `ChatModel` that returns a `ChatResponse` with tool calls; assert `removeInstance` does **not** execute without approval and the loop cap fires.

### 6c. Phase 5 — agent frontend (LOW)
Extend `ChatPage.jsx` (or a new `/chat` agent mode): handle `tool_call` + `confirmation_request` SSE events, render an expandable tool-call trace per turn, a confirm/cancel modal that blocks input for write/destructive tools, a read-only toggle, and "Copy as .env / Spring config" when a `stackSummary` result is present.

### 6d. Phase 4 — RAG integration test (MEDIUM)
Testcontainers IT: spin Postgres with pgvector, run `IngestionService` on a known log blob, assert an `ERROR` chunk is retrieved above an `INFO` one for a generic query. Also wire `IngestionService` to re-index on deploy/remove + a schedule, and `RollingSummaryService.summariseIfNeeded` `@Async` after replies (needs `chat_session`/`chat_message` JPA entities + repos — not yet created; `V7` tables exist).

### 6e. Phase 5 — Pull button → real pipeline (LOW)
Today the Model Cookbook "Pull" button copies `ollama pull <tag>`. To make it fire `ModelPullStep` for real, first add a path to **deploy Ollama as a managed runtime** (e.g. an `OLLAMA` catalog entry or a `ModelRuntime` deploy flow using the existing pipeline), persist to `model_runtime`/`pulled_model` (V6 tables exist, no entities yet), then trigger `ModelPullStep` against that runtime's port.

### 6f. Phase 6 — launch polish (LATER)
README overhaul, demo GIF, native installer verification (roadmap §8). Then open the **single PR `feature/port-wrangler-ai` → `master`**.

---

## 7. Gotchas learned this session (save yourself the pain)

1. **`.system(text)` overrides** the default system prompt — always concatenate `ChatClientConfig.SYSTEM_PROMPT + context`.
2. **Record boolean components named `isX`** confuse Jackson — name it `containerGpu`, not `isContainerGpu` (see `SystemProfile`).
3. **`GpuDetector.classify` is `public`** (the engine in another package calls it). Don't revert to package-private.
4. **`QuestionAnswerAdvisor` needs `spring-ai-advisors-vector-store`** — not pulled by the pgvector starter.
5. **One Spring constructor** on `DockerDeployEngine` (`GpuHostConfigurer`) — two constructors break injection.
6. **Frontend EventSource auto-reconnects** — always `es.close()` on the `done` event and in `onerror`, or the prompt re-fires.
7. **`git filter-branch -- --all` rewrites `master`/tags/remotes** (message normalization changes SHAs even with no textual change). Scope rewrites to `master..<branch>` only. Backups live in `refs/original/` — restore from there if you over-scope.
8. **MockMvc standalone** for controllers avoids loading interceptors/`AppConfig`/Docker — use it.
9. Spotless (`googleJavaFormat`) is enforced by CI — run `./gradlew spotlessApply` before committing.

---

## 8. How to verify quickly

```bash
# backend (from backend/)
./gradlew spotlessApply spotlessCheck compileJava compileTestJava
./gradlew test --tests "com.dbdeployer.ai.*"      # AI/RAG unit tests
./gradlew clean spotlessCheck test                # full gate (DeployPipelineIT skips w/o Docker)

# frontend (from frontend/)
npx eslint src/pages/<file>.jsx                    # new files must be lint-clean
npm run build                                      # must succeed

# dependency resolution check before using a new artifact
./gradlew dependencies --configuration runtimeClasspath | grep <artifact>
```

`DeployPipelineIT` needs a real Docker socket; it skips gracefully without one. The 6c/6b agent loop only truly exercises against a running Ollama (`PORTWRANGLER_OLLAMA_BASE_URL`, default `http://localhost:11434`).

---

## 9. Definition of Done (per phase tail, before merging to `feature`)

1. `./gradlew build spotlessCheck` passes.
2. Every new Java class has ≥1 unit test.
3. New REST endpoints have a slice/`@SpringBootTest` test.
4. New Liquibase changesets apply on a fresh DB.
5. Frontend renders without console errors, matches Tailwind tokens.
6. `git log --oneline` shows one commit per logical unit, `feat(phaseN):` prefix, **no co-author trailer**.

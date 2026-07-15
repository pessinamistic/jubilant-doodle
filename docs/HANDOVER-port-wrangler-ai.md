# Port Wrangler AI — Agent Handover

> **Purpose:** resume the Port Wrangler AI build exactly where the previous agent left off.
> **Read order:** this doc → `CONTEXT.md` → `docs/port-wrangler-roadmap-grounded.md` (deep technical source)
> → `docs/port-wrangler-future-roadmap.md` (what to build next, code-grounded).
> **Last updated:** session ending on branch `feature/port-wrangler-ai` @ `c6ff692`.

---

## 1. TL;DR — where we are

The 6-phase roadmap to turn Port Wrangler into an AI-agent infra cockpit is **functionally complete
through Phase 5**. The agent can be driven end-to-end from the UI with a confirmation-gated tool loop.

| Phase | Status | Notes |
|---|---|---|
| 0 — Hardening | ✅ (on `master`) | Liquibase present; `ddl-auto` cutover still pending (see §6) |
| 1 — Quick wins (Compose export, Spring config) | ✅ merged | |
| 2 — LLM Runtime + Model Cookbook | ✅ merged | |
| 3 — Spring AI foundation + Model Comparison | ✅ merged | |
| 4 — RAG foundation + **chat wiring** | ✅ merged | RAG context now injected into the live chat path |
| 5 — Agentic tools + MCP + **confirmation loop + frontend** | ✅ merged | the agent UI is live at `/agent` |

**Integration branch:** `feature/port-wrangler-ai` (tip `c6ff692`). Everything merges here; a **single PR
into `master` happens at the very end**. `master` is `7563946`, **untouched** — never rewrite it.

**Current working branch:** `feature/port-wrangler-ai` (all phase tails merged in, clean).

The whole backend passes `./gradlew clean spotlessCheck test` (**27 test classes**). Frontend `npm run
build` + `eslint` are clean.

---

## 2. What landed THIS session (since the previous handover @ `070ea56`)

Three logical units, each on its own phase-tail branch, each merged `--no-ff` into `feature`:

1. **`feat(phase4): wire RAG context into chat path`** (`4a34da1`, merged `941d84b`)
   - `RagChatService` now injects `MemoryRetriever` + `SmartContextBuilder`. Per turn it best-effort
     retrieves top-4 memories (try/catch — degrades to plain chat on pgvector failure) and prepends a
     smart-context block as `ChatClientConfig.SYSTEM_PROMPT + context` (never clobbers the base prompt).
   - Used **Option B** from the old handover (no `spring-ai-advisors-vector-store` dependency).
   - `rollingSummary` is passed as **`null`** for now (no `chat_session` persistence yet).
   - Tests: empty-context unchanged behaviour, memory-injection-into-system-prompt, graceful-degrade-on-throw.

2. **`feat(phase5): confirmation-gated agentic tool execution loop`** (`4378d7d`, merged `1c78ba3`)
   - **`AgentChatService`** — the manual tool loop: `chatModel.call(prompt)` → while `hasToolCalls()` →
     emit `tool_call` events → gate → `ToolCallingManager.executeToolCalls(...)` → rebuild prompt from
     `conversationHistory()` → repeat. `internalToolExecutionEnabled(false)` so the model proposes but
     we decide. Read-only tools auto-run; write/destructive halt with `confirmation_request` until the
     caller re-requests `approve=true`; `MAX_TOOL_ROUNDS=6` cap; `executeToolCalls` wrapped in try/catch.
   - **`AgentEvent`** record (SSE payload) + **`AgentChatController`** `GET /chat/agent/stream?message&model&baseUrl&approve`.
   - **`ModelRouter.chatModelFor(...)`** added (returns the `ChatModel` interface so the loop is
     unit-testable) and the two existing client builders were de-duplicated through it.
   - **`ChatClientConfig`** gained a `ToolCallingManager` bean.
   - Tests: destructive-not-executed-without-approval, executes-once-approved, read-only-never-executes-write,
     read-only-auto-runs, **loop-cap fires** (verified `listAll()` called exactly `MAX_TOOL_ROUNDS` times),
     plus a MockMvc async-dispatch slice for the controller.

3. **`feat(phase5): agent frontend with tool-call trace + confirmation flow`** (`2972daf`, merged `6cc4fb0`)
   - **`AgentPage.jsx`** at `/agent` (route in `App.jsx`, nav in `AppShell.jsx` desktop+mobile, `Wrench` icon).
     Renders the tool-call trace (expandable rows + status badges + JSON args), a confirm/cancel modal
     (reuses `ConfirmModal`; red for destructive, amber for write), a **client-side read-only toggle**
     (blocks approvals), and approve-resume. `EventSource` always closed on `done`/`onerror`.
   - **Backend contract hardening:** renamed the agent SSE error event `error` → **`agent_error`** so it no
     longer collides with EventSource's reserved `error` (connection) event. Updated 2 test assertions.

Plus **`docs: add Port Wrangler forward roadmap`** (`c6ff692`) — `docs/port-wrangler-future-roadmap.md`.

---

## 3. Conventions (follow exactly)

- **Commits:** one per logical unit, prefix `feat(phaseN): …` / `docs: …`. **DO NOT add a `Co-authored-by`
  trailer** (the user disabled it; earlier commits were rewritten to remove it). This project rule
  overrides any global default.
- **Branching:** create each tail branch from the tip of `feature/port-wrangler-ai`; merge back with
  `git merge --no-ff`. Never branch from another phase branch. **Never merge into `master`.**
- **Only stage your own files.** Pre-existing dirty/untracked files live in the tree — **do not commit them**:
  `.gitignore`, `backend/.../model/ImageTrackingStatus.java`, `backend/.../service/ImageValidationService.java`,
  and untracked `CONTEXT.md`, `backend/AGENTS.md`, `backend/CLAUDE.md`, `opencode.json`, `graphify-out/`,
  `docs/prompt`, `frontend/jsconfig.json`. Stage files by explicit path, never `git add -A`.
- **Schema:** Liquibase only, `db/changelog/changes/V<N>__<snake>.sql`, `--changeset portWrangler:<N>
  labels:phase<N>`, registered via `includeAll`. Never Flyway, never `ddl-auto`.
- **Java package stays `com.dbdeployer`.** Frontend is plain JSX (no TS) — match the file you edit.
- **Verify-before-build:** confirm any new dependency resolves (`./gradlew dependencies --configuration
  runtimeClasspath | grep <artifact>`) *and* inspect the actual jar API with `javap` before writing code
  against it. (This session relied on `javap` to nail the Spring AI tool-calling API.)
- **Test pattern:** controllers → `MockMvcBuilders.standaloneSetup(controller)`; SSE controllers →
  add async-dispatch (`asyncDispatch`) since the body is a `Flux`; services → Mockito mocks; Spring AI →
  stub `ChatModel` (see `RagChatServiceTest`, `AgentChatServiceTest`). Algorithm cores are extracted so
  they're unit-testable without live infra.

---

## 4. Hard constraints (non-negotiable)

| Constraint | Correct |
|---|---|
| Spring AI version | **`1.0.3` GA** (BOM pins it) — never a milestone/RC/1.1.x |
| Vector store | pgvector on the existing Postgres (no Chroma/Pinecone) |
| Web stack | servlet (`spring-boot-starter-web`) — **no WebFlux/R2DBC**. SSE returns `Flux<ServerSentEvent>` from MVC. |
| LLM runtime | Ollama default |
| Schema | Liquibase changeset |
| GPU detect | Docker SDK + `/dev` probes — **never** shell `nvidia-smi`; `GpuDetector.classify` is **public** |
| pgvector index | HNSW (not IVFFlat), cosine, 768 dims (fixed) |

---

## 5. Inventory (current)

### Backend `com.dbdeployer`
- **`ai/`** — `ChatClientConfig` (ChatMemory window=8, **`ToolCallingManager` bean**), `ModelRouter`
  (`clientFor`/`statelessClientFor`/**`chatModelFor`**), `RagChatService` (**RAG-grounded** SSE stream),
  `ModelComparisonService`, `AgentChatService` (**confirmation-gated tool loop**), `AgentEvent`,
  `ModelSelection`/`ChatToken`/`ComparisonChunk`.
- **`ai/` RAG** — `RecencyFrequencyScorer`, `TokenBudget`, `LogChunker`, `IngestionService`,
  `MemoryRetriever`, `RollingSummaryService`, `ScoredChunk`, `SmartContextBuilder`.
- **`ai/tools/`** — `InfrastructureTools` (read-only: `listInstances/readLogs/connectionConfig/stackSummary`;
  destructive: `stopInstance/removeInstance`), `AgentSafety` (`MAX_TOOL_ROUNDS=6`, `DESTRUCTIVE`,
  **`WRITE = {deployDatabase, createKafkaTopic, pullModel}` ← classified but NOT yet implemented**),
  `InstanceSummary`/`StackSummary`.
- **`mcp/`** — `McpServerConfig` (read-only by default; `portwrangler.mcp.write-enabled=true` exposes writes).
- **`runtime/`** — `ModelRuntime`, `ModelCatalog`, `GpuDetector`, `ModelSuggestionService`, `SystemProfile`, etc.
- **`deploy/`** — `DockerDeployEngine`, `GpuHostConfigurer`, `ConnectionStringBuilder` (`springBootProperties`).
- **`pipeline/step/`** — `ModelPullStep` (`StepType.PULL_MODEL`).
- **`api/`** — `ComposeExportController`, `ModelCookbookController`, `ChatController` (`/chat/stream`),
  `AgentChatController` (`/chat/agent/stream`), `ModelComparisonController`, `DbInstanceController.springConfig`.

### Migrations
`V5__baseline` · `V6__add_model_runtime` · `V7__add_chat_memory` · `V8__enable_pgvector`. (V6/V7 have **no
JPA entities yet** — tables exist, entities to be written when needed.)

### Frontend `frontend/src`
- Pages: `ModelCookbookPage` (`/models`), `ChatPage` (`/chat`), **`AgentPage` (`/agent`)**, `ComparePage` (`/compare`).
- `App.jsx` routes + `AppShell.jsx` nav updated (Assistant + Agent).
- The agent SSE event contract: `tool_call`, `confirmation_request`, `token`, `agent_error`, `done`.

### Config
- `build.gradle.kts`: Spring AI BOM 1.0.3 + starters `model-ollama`, `model-chat-memory-repository-jdbc`,
  `vector-store-pgvector`, `mcp-server`.
- `application.yml`: `spring.ai.ollama` (lazy), chat-memory `initialize-schema: never`, pgvector
  (768/HNSW/cosine, `initialize-schema` false), mcp server name/version; flags
  `portwrangler.agent.read-only` + `portwrangler.mcp.write-enabled`.

---

## 6. Remaining work (priority order — from `docs/port-wrangler-future-roadmap.md`)

### 6a. CI Docker boot test — **HIGH, do early**
`DeployPipelineIT` (`@SpringBootTest`, `disabledWithoutDocker`) now boots Ollama + chat-memory + pgvector
+ MCP + tool-calling autoconfig. **It has never been run in-sandbox (no Docker socket).** Run it under
Docker/CI and confirm context startup is green. Mitigations already in `application.yml`.

### 6b. The north-star feature — implement the WRITE tools — **HIGH**
`AgentSafety.WRITE` lists `deployDatabase`, `createKafkaTopic`, `pullModel` but `InfrastructureTools`
doesn't implement them. Add them as thin `@Tool` wrappers (`DbInstanceService.deploy`, exec via
`DockerDeployEngine`, `ModelPullStep`). They inherit the confirmation gate + MCP surface + UI trace for
free. Pairs with deploying **Ollama as a managed runtime** (roadmap §2.1) for `pullModel`.

### 6c. Liquibase cutover — **HIGH**
Flip `ddl-auto: update` → `validate`, enable `spring.liquibase`. Write a baseline reconcile changeset if drift.

### 6d. Agent statefulness — **MEDIUM**
- **Stateful confirmation resume:** today approval re-runs the whole turn (read-only tools execute twice).
  Add a `PendingConfirmation` registry keyed by `confirmationId` + a `POST /chat/agent/confirm/{id}` that
  resumes from saved conversation history.
- **Multi-turn agent memory:** give `AgentChatService` the `sessionId`/`MessageChatMemoryAdvisor` window.

### 6e. RAG persistence + IT — **MEDIUM**
`chat_session`/`chat_message` JPA entities + repos (V7 tables exist) → wire `RollingSummaryService`
`@Async` after replies and pass a real `rollingSummary` into `RagChatService` (currently `null`).
`IngestionService` re-index on deploy/remove + a schedule. Testcontainers IT: pgvector asserts an `ERROR`
chunk outranks an `INFO` chunk.

### 6f. Compose import + recipes — **MEDIUM**
Symmetric to `ComposeExportService`: parse a `docker-compose.yml` → `DeploymentConfig`s → pipeline. Then
stack bundles (Postgres+Redis+Kafka in one click). Both are great agent tools too.

### 6g. Launch polish — **LATER**
README overhaul, demo GIF, jpackage DMG/EXE verification, then the **single PR `feature/port-wrangler-ai`
→ `master`**.

> Full feature catalogue with effort/value/integration-points is in `docs/port-wrangler-future-roadmap.md`.

---

## 7. Gotchas learned (save yourself the pain)

1. **`.system(text)` overrides** the default system prompt — always concatenate `ChatClientConfig.SYSTEM_PROMPT + context`.
2. **EventSource reserves the `error` event** for connection failures — never name a server SSE event `error`
   (we use `agent_error`). Always `es.close()` on `done`/`onerror` or the prompt re-fires (auto-reconnect).
3. **`ToolCallbacks.from(...)` does NOT exist** in the resolved 1.0.3 jars. Get callbacks via
   `MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks()` (as `McpServerConfig` does).
4. **`QuestionAnswerAdvisor` needs `spring-ai-advisors-vector-store`** — not pulled by the pgvector starter.
   We do RAG grounding manually instead (Option B in `RagChatService`).
5. **Manual tool loop API (verified via `javap`):** `ToolCallingChatOptions.builder().toolCallbacks(list)
   .internalToolExecutionEnabled(false).build()`; `ToolCallingManager.builder().build()`;
   `manager.executeToolCalls(prompt, response)` → `result.conversationHistory()` → `new Prompt(history, options)`.
   Tool calls: `response.getResult().getOutput().getToolCalls()` (`AssistantMessage.ToolCall` record: `.name()`, `.arguments()`).
6. **Return a `ChatModel` interface, not `OllamaChatModel`,** from `ModelRouter.chatModelFor` — the concrete
   class can't be stubbed in unit tests.
7. **`GpuDetector.classify` is `public`**; **one Spring constructor** on `DockerDeployEngine`; **record boolean
   components named `isX`** confuse Jackson (use `containerGpu`, not `isContainerGpu`).
8. **MockMvc + SSE:** a `Flux<ServerSentEvent>` controller needs `request().asyncStarted()` then
   `mockMvc.perform(asyncDispatch(result))` to read the body.
9. **`graphify update .` is blocked in the sandbox** (venv `PermissionError`) — run it in a normal shell after
   code changes to refresh `graphify-out/`. Don't work around the sandbox.
10. **`git filter-branch -- --all` rewrites `master`/tags** — scope any rewrite to `master..<branch>` only.

---

## 8. How to verify quickly

```bash
# backend (from backend/)
./gradlew spotlessApply spotlessCheck compileJava compileTestJava
./gradlew test --tests "com.dbdeployer.ai.*"      # AI/RAG/agent unit tests
./gradlew clean spotlessCheck test                # full gate (DeployPipelineIT skips w/o Docker)

# frontend (from frontend/)
npx eslint src/pages/<file>.jsx                    # new files must be lint-clean
npm run build                                      # must succeed

# inspect a Spring AI jar API before coding against it
javap -cp <jar> org.springframework.ai.model.tool.ToolCallingManager
```

`DeployPipelineIT` needs a real Docker socket; it skips gracefully without one. The agent loop only truly
exercises against a running Ollama (`PORTWRANGLER_OLLAMA_BASE_URL`, default `http://localhost:11434`).

Manual smoke once Ollama is up: open the UI → `/agent` → "list my running instances" (auto-runs) →
"stop the X instance" (should surface the confirm modal; cancel = no-op, approve = executes).

---

## 9. Definition of Done (per phase tail, before merging to `feature`)

1. `./gradlew clean spotlessCheck test` passes.
2. Every new Java class has ≥1 unit test.
3. New REST endpoints have a slice test (SSE → MockMvc async-dispatch).
4. New Liquibase changesets apply on a fresh DB.
5. Frontend renders without console errors, `eslint` clean, `npm run build` succeeds, matches Tailwind tokens.
6. `git log --oneline` shows one commit per logical unit, `feat(phaseN):` prefix, **no co-author trailer**.
7. Merge the tail into `feature/port-wrangler-ai` with `--no-ff`. `master` stays untouched.

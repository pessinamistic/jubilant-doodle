# Port Wrangler — Forward Roadmap (what to build next)

> Written after Phases 0–5 landed on `feature/port-wrangler-ai`. Grounded in the real code:
> God nodes `DbInstanceService` (46 edges), `DockerDeployEngine` (74), `DeploymentConfig` (57),
> the `DeployStep` pipeline SPI, the `ai/` + `ai/tools/` + `mcp/` packages, and migrations V5–V8.
> Read with `CONTEXT.md` and `docs/port-wrangler-roadmap-grounded.md`.

Legend — **Effort:** S (≤½ day) · M (1–2 days) · L (3–5 days). **Value:** ★–★★★.

---

## 0. Finish the current arc (committed work from the handover)

These were already scoped; they close out Phases 4–6.

| # | Item | Where it plugs in | Effort | Value |
|---|------|-------------------|--------|-------|
| 0.1 | **CI Docker boot test (6a)** — run `DeployPipelineIT` `@SpringBootTest` under Docker/CI so the Spring AI + MCP + pgvector autoconfig boot is proven green. | `backend` CI (`.github/workflows/ci`), `application.yml` mitigations already in place | S | ★★★ |
| 0.2 | **Liquibase cutover** — flip `ddl-auto: update` → `validate`, enable `spring.liquibase`, write `V5__baseline` reconcile if drift remains. | `application.yml`, `db/changelog` | M | ★★★ |
| 0.3 | **RAG persistence (6d)** — `chat_session` + `chat_message` JPA entities/repos (V7 tables exist), wire `RollingSummaryService.summariseIfNeeded` `@Async` after replies, and pass a real `rollingSummary` into `RagChatService` (today it's `null`). | `ai/RollingSummaryService`, `ai/RagChatService`, new `model/` entities | M | ★★ |
| 0.4 | **RAG re-index + IT (6d)** — `IngestionService` re-index on deploy/remove + a schedule; Testcontainers IT (pgvector) asserting an `ERROR` chunk outranks an `INFO` one. | `ai/IngestionService`, `DbInstanceService` hooks, `@Scheduled` | M | ★★ |
| 0.5 | **Pull button → real pipeline (6e)** — deploy Ollama as a managed runtime, persist `model_runtime`/`pulled_model` (V6 tables exist), fire `ModelPullStep` for real. | `pipeline/step/ModelPullStep`, `runtime/`, `store/DatabaseCatalog` | L | ★★ |
| 0.6 | **Launch polish (6f)** — README overhaul, demo GIF, jpackage DMG/EXE verification, then the single PR `feature/port-wrangler-ai` → `master`. | repo root, installers | M | ★★★ |

---

## 1. Agent depth — the highest-leverage area (the confirmation loop is now live)

The agent loop + confirmation gate + frontend trace all exist. The cheapest big wins are now **more tools** and **statefulness**, because every new `@Tool` instantly inherits gating, the MCP surface, and the UI trace.

### 1.1 Implement the classified-but-missing WRITE tools — **★★★, M**
`AgentSafety.WRITE = {deployDatabase, createKafkaTopic, pullModel}` is already declared, but **`InfrastructureTools` only implements the read-only + destructive tools** — these three write tools don't exist yet. Add them as thin wrappers (same battle-tested paths):
- `deployDatabase(type, version, name)` → `DbInstanceService.deploy(DeploymentConfig)`.
- `createKafkaTopic(instanceName, topic, partitions)` → exec against the running Kafka container via `DockerDeployEngine`.
- `pullModel(runtimeName, tag)` → `ModelPullStep` (pairs with 0.5).

They flow through the existing confirmation modal automatically. **This is the single most natural next feature** — it makes "deploy a redis and give me the Spring config" actually work, which is the project's north-star sentence.

### 1.2 Stateful confirmation resume — **★★, M**
Today approval re-runs the whole turn (`approve=true`), so read-only tools execute twice. Add a `PendingConfirmation` registry keyed by a `confirmationId` that stores the `Prompt` conversation history + the pending `ChatResponse`; a `POST /chat/agent/confirm/{id}` resumes exactly where it paused. Removes double-execution and makes writes truly idempotent-safe.

### 1.3 Multi-turn agent memory — **★★, M**
The agent loop is stateless per turn (no `sessionId`). Give it the same `MessageChatMemoryAdvisor`/`chat_session` window the plain chat uses so "now remove the one you just listed" works.

### 1.4 "Copy as .env / Spring config" affordance — **★, S**
When a turn's tool trace contains `stackSummary`/`connectionConfig`, render a one-click "Copy as .env" / "Copy Spring config" button in `AgentPage` (reuse `ConnectionStringBuilder.springBootProperties`). Finishes the handover's 6c stretch goal.

### 1.5 Richer tool result rendering — **★, S–M**
Render structured tool outputs (e.g. `listInstances`) as a table inside the trace instead of model-summarised prose; surface `returnDirect` tools verbatim.

---

## 2. Model runtime productization (builds on `runtime/` + `GpuDetector`)

### 2.1 Ollama-as-managed-runtime — **★★★, L**
Add an `OLLAMA`/`MODEL_RUNTIME` path so the Cookbook can deploy a runtime through the normal pipeline (GPU host config already exists via `GpuHostConfigurer`). Unblocks 0.5 and 1.1's `pullModel`.

### 2.2 Pulled-model lifecycle UI — **★★, M**
List/delete pulled models per runtime (persist `pulled_model`), show disk usage, "set default model" for chat/agent/compare. Surface `GpuDetector.classify` compatibility inline.

### 2.3 Model warm-up & health — **★, S**
A readiness probe + "warm" button that sends a trivial prompt so first real latency isn't cold-start; show per-model status dot like the Docker pill.

---

## 3. Deployment & catalog (builds on `DeploymentConfig`, `DatabaseCatalog`, the pipeline)

### 3.1 Docker Compose **import** (reverse of export) — **★★★, M**
You already have `ComposeExportService`. The symmetric feature — parse a `docker-compose.yml` into `DeploymentConfig`s and run them through the pipeline — is high-value and reuses everything. Great agent tool too (`importCompose`).

### 3.2 Stack recipes / bundles — **★★, M**
One-click multi-service bundles (e.g. "Event-driven starter" = Postgres + Redis + Kafka + Kafka-UI) as a new `store/` concept, deployed as a correlated pipeline batch. Pairs beautifully with the agent ("spin me up an event-driven stack").

### 3.3 Catalog expansion — **★, S each**
Add high-demand types to `DatabaseCatalog`: ClickHouse, NATS, MinIO, Temporal, Valkey. Each is a `DbDefinition` entry — cheap, additive, immediately available to UI + agent.

### 3.4 Dependency-aware ordering — **★★, M**
Let a config declare `dependsOn` so bundles start in order (DB before the app that needs it). Extends `PipelineOrchestrator`.

---

## 4. Observability (builds on `getContainerMetrics`, `DashboardPage`, actuator/Prometheus)

### 4.1 Live metric thresholds & alerts — **★★, M**
Per-instance CPU/mem thresholds → toast + status badge when breached (reuse the existing metrics polling + Recharts). Add an agent tool `instanceHealth(name)` so the model can reason about a hot container.

### 4.2 Log search / tail UI — **★★, M**
A streaming log viewer (SSE) with severity filter, backed by `getLogs` + the `LogChunker` severity tagging you already wrote for RAG. Doubles as ground truth for the agent's `readLogs`.

### 4.3 Event/audit timeline — **★, M**
Persist agent actions + deploy lifecycle to an `audit_log`, ingest into the vector store (extends `IngestionService`) so the agent can answer "what did I change yesterday?".

---

## 5. Platform & reach

### 5.1 Remote / multi-context Docker — **★★, L**
`DockerSocketResolver` already abstracts the socket. Allow targeting remote daemons / Docker contexts so Port Wrangler manages a dev box or a teammate's host. Touches `DockerDeployEngine` ctor + a `host` field on config.

### 5.2 MCP write-flows hardened — **★★, M**
`McpServerConfig` strips destructive tools unless `mcp.write-enabled`. Add a confirmation-token handshake for MCP write tools so external IDE agents (Cursor/Claude Desktop) get the same safety as the in-app agent.

### 5.3 Agent-native CLI — **★, M**
A thin CLI over `DbInstanceService` (`pw deploy redis`, `pw ls`, `pw rm`) — same code paths, scriptable. (See the `printing-press` skill for scaffolding.)

### 5.4 Secrets vault — **★★, L**
You already mask credentials (`ConnectionStringBuilder.buildMasked`). Add encrypted-at-rest secret storage + rotation, surfaced in connection configs and `.env` export.

---

## 6. Technical-debt / hardening backlog (do continuously)

- **Frontend code-splitting** — the bundle is 913 kB; lazy-load `ChatPage`/`AgentPage`/`ComparePage`/`DashboardPage` via `React.lazy` (the build already warns).
- **Liquibase `validate` mode** (0.2) — stop relying on `ddl-auto: update`.
- **Agent loop polish** — handle `returnDirect` tools (skip the extra model round), stream the final turn token-by-token instead of one block.
- **Test coverage** — Testcontainers IT for pgvector (0.4) and a real-Ollama smoke test behind a profile.
- **`graphify update .`** in CI (non-sandboxed) so the knowledge graph stays current.

---

## Recommended sequence (max value, min risk)

1. **0.1 CI Docker boot** + **0.2 Liquibase cutover** — lock the foundation before adding surface.
2. **1.1 WRITE tools** (+ **2.1 Ollama runtime** as its dependency) — delivers the north-star "deploy X and give me config" sentence end-to-end.
3. **1.2 stateful resume** + **1.3 agent memory** — makes the agent feel real and safe.
4. **3.1 Compose import** + **3.2 recipes** — big perceived-power jump, low new infra.
5. **0.3/0.4 RAG persistence + IT** — turns the RAG plumbing into felt value (the agent "remembers").
6. **4.x observability** — differentiation once the core loop is solid.
7. **0.6 launch polish** → single PR to `master`.

---

## Quick map: feature → primary integration point

| Feature | Extend this |
|---|---|
| New agent capability | `ai/tools/InfrastructureTools` (`@Tool`) + `AgentSafety` classification |
| New long-running work | a new `DeployStep` impl (`pipeline/step/…`) |
| New service type | `store/DatabaseCatalog` (`DbDefinition`) |
| New deploy behaviour | `DbInstanceService` (facade) / `DockerDeployEngine` |
| New schema | Liquibase `db/changelog/changes/V<N>__*.sql` |
| New external-agent tool | `mcp/McpServerConfig` (auto-inherits from `InfrastructureTools`) |
| New UI page | `frontend/src/pages` + route in `App.jsx` + nav in `AppShell.jsx` |

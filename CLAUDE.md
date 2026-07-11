# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Project overview

**Port Wrangler** (Java package `com.dbdeployer`, repo name `jubilant-doodle`) is a local self-serve portal for deploying and managing 14+ databases/infra tools (Postgres, MySQL, MongoDB, Redis, Kafka, etc.) on Docker. It's a fullstack app: Spring Boot 3.4 / Java 21 backend + React 19 / Vite frontend, single JAR deploy (frontend built into `classpath:/static/`). It is being extended with a local-LLM feature set: Spring AI chat, RAG over pgvector, an agentic `@Tool`-calling assistant, and an MCP server exposing the same tools to external clients (VS Code / Claude / Cursor).

## Common commands

**Backend** (run from `backend/`):
- `./gradlew bootRun` — start the API on `:8080` (auto-provisions a `pgvector/pgvector:pg16` system-DB container on `:5499` on first run via `SystemDbProvisioner`)
- `./gradlew build spotlessCheck` — full build + format check (this is what CI runs)
- `./gradlew test` — run all backend tests
- `./gradlew test --tests "com.dbdeployer.service.DbInstanceServiceTest"` — run a single test class
- `./gradlew test --tests "com.dbdeployer.service.DbInstanceServiceTest.deploysNewInstance"` — run a single test method
- `./gradlew spotlessApply` — auto-format Java (Google Java Format via Spotless; CI only checks, doesn't fix)
- `./gradlew liquibaseChangelogSync` — one-time, existing installs only, run against a *running* system DB before enabling/upgrading Liquibase changesets
- `./gradlew jpackageInstaller` (`-Pjpackage.type=dmg|exe|app-image`) — build a native installer, output in `backend/build/dist/`

**Frontend** (run from `frontend/`):
- `npm install`
- `npm run dev` — Vite dev server on `:5173`, proxies API calls to `:8080`
- `npm run build` — production build to `dist/`
- `npm run lint` — ESLint

**Full stack via Docker Compose** (from repo root): `docker compose up --build` — serves everything from `:8080`.

Integration tests (`DeployPipelineIT`, Testcontainers-based) need a real Docker socket and skip gracefully under Docker Desktop's Enhanced Container Isolation via `@Testcontainers(disabledWithoutDocker=true)`; they run for real on CI (ubuntu-latest).

## Architecture

### Backend package layout (`backend/src/main/java/com/dbdeployer/`)

- **`api/`** — REST controllers (`DbInstanceController`, `ConfigTemplateController`, `CatalogController`, `ImageController`, `AgentChatController`, ...) and their `dto/` request/response types. All endpoints are under `/api` (`server.servlet.context-path`).
- **`deploy/`** — `DatabaseCatalog` (the 14+ `DbType` definitions), `DockerDeployEngine` (Docker Java SDK integration — the highest-fan-out class in the graph), `BrewDeployEngine` (fallback), `ConnectionStringBuilder`, `OsDetector`.
- **`pipeline/`** — the async 4-step deploy pipeline: `PipelineOrchestrator` creates `DeploymentPipeline`/`PipelineStep` rows transactionally, then fires `PipelineRunner` via an `afterCommit` hook (avoids a commit-race). Steps live in `pipeline/step/` (`ImagePullStep`, `ContainerCreateStep`, `ContainerStartStep`, `FinaliseStep`) implementing a shared `DeployStep` SPI — **reuse this SPI + `PipelineRunner` for any new long-running background operation** (e.g. model pulls), don't invent a parallel mechanism.
- **`service/`** — business logic: `DbInstanceService` (deploy/start/stop/sync/import — a god object, most call paths run through it), `ImageValidationService` (local + Docker Hub tag existence checks), `DockerHubTagClient`, `AsyncDeployer`.
- **`model/` / `store/`** — JPA entities and Spring Data repositories: `DeploymentConfig`, `DeployedContainer` (the two most-connected nodes in the codebase), plus AI-feature entities `ChatSession`/`ChatMessage`, `ModelRuntimeEntity`, `PulledModel`.
- **`config/`** — `AppConfig` (CORS, async executor), `SystemDbProvisioner`/`SystemDbRegistrar` (auto-pulls and starts the system Postgres *before* any Spring bean initializes, via `ApplicationContextInitializer`), `DockerSocketResolver` (Docker Desktop / Colima / Linux socket detection), `StatusSyncScheduler`, `ImageTrackingScheduler`, `DeploymentRecovery` (resumes in-flight deployments on restart).
- **`ai/`** — the LLM feature set: `ChatClientConfig` (Spring AI beans), `ModelRouter` (routes chat requests to a runtime-selected model), `AgentChatService` (SSE-streamed agentic chat with tool-calling), `RagChatService`/`IngestionService`/`MemoryRetriever`/`LogChunker` (pgvector-backed RAG), `RollingSummaryService`/`TokenBudget` (context-window management for long chats), `ModelComparisonService`.
- **`ai/tools/`** — `@Tool`-annotated classes the agent can call: `InfrastructureTools` (read/write infra ops), `AgentSafety` (guardrails — destructive tools require human confirmation via manual tool execution: `internalToolExecutionEnabled(false)` + `ToolCallingManager.executeToolCalls`, never auto-execute).
- **`mcp/`** — `McpServerConfig`, exposes the same `@Tool`s as an MCP server (Streamable-HTTP transport at `/api/mcp`) for external agent clients.
- **`runtime/`** — local-LLM runtime management: `ModelCatalog`/`ModelDefinition` (the model cookbook), `GpuDetector`/`GpuVendor`/`CompatibilityLevel` (hardware-aware model suggestions), `OllamaAdminClient`/`OllamaModelPuller`/`OllamaLibrarySearchService` (talks to a deployed Ollama container), `ModelRuntimeService`, `ModelSuggestionService`.
- **`event/`** — `InstanceDeployedEvent`/`InstanceRemovedEvent`, Spring application events for cross-cutting reactions (e.g. RAG re-ingestion triggers).
- **`os/`, `startup/`, `validations/`** — `OperatingSystemService` (OS/tool detection for the UI), `DockerStartupCheck` (fails fast with OS-specific remediation if Docker is unreachable), `DeploymentValidations`.

### Schema management

Liquibase (`db/changelog/`) is authoritative — `spring.jpa.hibernate.ddl-auto: validate` (Hibernate checks entity mappings but never mutates schema). **All schema changes must go through a new Liquibase changeset**, never by relying on Hibernate auto-DDL. `H2DataMigrator` is a one-shot legacy migrator (H2 → Postgres); it and its H2 dependency should be deleted once all installs have migrated — don't build on top of it.

### AI feature constraints (see memory: Port Wrangler roadmap)

- Spring AI is pinned to the BOM version in `backend/build.gradle.kts` (currently 1.1.x GA) — check that file for the exact pin before assuming a version.
- "Zero-extra-service" principle: RAG uses pgvector as an extension on the *existing* system Postgres container, not a separate vector DB service. Local LLM inference goes through a deployed Ollama container, not an embedded runtime.
- Read-only vs. destructive tool exposure is config-gated: `portwrangler.agent.read-only` (in-app agent) and `portwrangler.mcp.write-enabled` (external MCP clients) in `application.yml`.

### Frontend (`frontend/src/`)

- **`api/client.js`** — single Axios layer for all backend calls.
- **`pages/`** — one file per route: deployment (`DeployPage`, `ConfigurationFormPage`, `InstancesPage`, `InstanceDetailPage`), image management (`ImageManagementPage`, `ImageToolPage`), AI features (`ChatPage`, `AgentPage`, `ComparePage`, `ModelCookbookPage`, `ModelDetailPage`, `RuntimePage`, `RuntimeModelDetailPage`), `SystemHealthPage`, `DashboardPage`, `HomePage`.
- **`components/`** — shared UI: `AppShell` (nav/layout), `DeployModal`/`ImportModal`/`ConfirmModal`, `InstanceCard`, `ConnectionString`, `StatusBadge`, `SystemBanner`, `ModelSelect`, `WelcomeWizard`/`SplashScreen`.
- **`theme/`** — `initializeTheme.js`/`themeCore.js` (light/dark mode, applied before React mounts to avoid flash), `ThemeProvider`/`useTheme`.

### Deployment pipeline (core flow)

```
PULL_IMAGE → CREATE_CONTAINER → START_CONTAINER → FINALISE
```
Every new deploy creates a `DeploymentPipeline` + `PipelineStep` rows, executed asynchronously and pollable via `GET /api/instances/{id}/pipeline`. Configurable pacing via `dbdeployer.pipeline.step-delay-ms`.

### API docs

Swagger UI is live at `/api/swagger-ui/index.html`, OpenAPI spec at `/api/v3/api-docs` (also checked into `backend/docs/open-api-spec.json`).

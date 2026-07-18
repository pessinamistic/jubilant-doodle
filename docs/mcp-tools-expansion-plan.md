# MCP/Agent Tool Surface Expansion Plan

> Status: **ON HOLD** — blocked on two pre-requisite fixes (2026-07-18):
> 1. `exportCompose` UX is wrong: the export button sits on the Instances page but must move into the Instance Detail page (export that one instance's compose); the Instances-page export should instead open a picker listing deployed instances so the user selects which ones to export.
> 2. Docker vs. Brew services are not distinguished in management; they should be separated (and, on macOS, optionally a brew-exploration UI over tapped repos).
>
> Remediation for both is planned in `docs/compose-export-and-engine-separation-plan.md`; implement those first, then resume this plan with the amendments below.
>
> **Amendments once un-held** (from the remediation investigation):
> 1. `exportCompose()` → `exportCompose(List<String> instanceNames)` — empty = whole stack; resolves names via `requireByName`, calls the new `ComposeExportService.exportYaml(configIds)` overload. Still read-only, still thin.
> 2. `containerMetrics` is Docker-only — document it as returning "unavailable" for brew-managed instances (guarded in the P2-A fix).
> 3. Add `deployMethod` to the tool layer's `InstanceSummary` record so agents/MCP clients see the Docker-vs-brew engine distinction.

## 1. Current state (verified 2026-07-18)

- All 9 tools live in `backend/src/main/java/com/dbdeployer/ai/tools/InfrastructureTools.java` (~300 lines), with helpers `requireByName(name)` (name → `DeployedContainer`) and `toSummary(...)` (compact record mapping).
- `AgentSafety` is method-name-keyed: `DESTRUCTIVE` and `WRITE` sets; read-only = anything not listed.
- `McpServerConfig.portWranglerMcpTools(...)` builds callbacks from a single `toolObjects(tools)`; `AgentChatService.exposedCallbacks()` (~line 149) independently builds from the same single object. Both must change if a new tool class is added.
- Tests to imitate: `InfrastructureToolsTest`, `AgentSafetyTest`, `McpServerConfigTest` (tests the pure `filterReadOnly`), `AgentChatServiceTest`.
- Current tools — read-only: `listInstances`, `readLogs`, `connectionConfig`, `stackSummary`; write: `deployDatabase`, `createKafkaTopic`, `pullModel`; destructive: `stopInstance`, `removeInstance`. Write/destructive stripped from MCP unless `portwrangler.mcp.write-enabled=true`.

## 2. Proposed tools (15)

### Tier 1 — high value, trivial thin wrapper

| Tool | Wraps | Safety | Value |
|---|---|---|---|
| `startInstance(String instanceName)` | `DbInstanceService.startInstance(configId)` via `requireByName` | write (recoverable mirror of stopInstance) | MCP clients can stop but never restart — glaring asymmetry |
| `deploymentStatus(String instanceName)` | `DbInstanceService.getLatestPipeline(configId)` → `PipelineResponse` | read-only | Closes the deploy loop (`deployDatabase` says "poll" but no tool can); also update `deployDatabase` description to point here |
| `listCatalog()` | `DatabaseCatalog.all()` → compact record (type, versions, defaultPort) | read-only | Discovery — stops clients guessing valid `type`/`version` args |
| `exportCompose()` | `ComposeExportService.exportYaml()` | read-only | Killer IDE feature; **revisit signature after the export-UX fix (per-instance / selection export will change the service API)** |
| `listModels()` | `ModelDashboardService.dashboard()` → trimmed summary | read-only | Prerequisite for model workflows; pairs with `pullModel`/`deleteModel` |
| `systemInfo()` | `OperatingSystemService.getSystemInfo()` + `DockerDeployEngine.isDockerAvailable()` | read-only | Lets an external agent diagnose "why did my deploy fail" |

### Tier 2 — high value, modest care

| Tool | Wraps | Safety | Care needed |
|---|---|---|---|
| `availableVersions(String type)` | `ImageTagVersionService.resolveVersions(DbType, refresh=false)` | read-only | Pin refresh=false (cached); cap list length |
| `checkImageTag(String type, String version)` | `ImageValidationService.checkForDeploy(DbType, tag)` → `ImageCheckResponse` | read-only | Verify no tracking-row side effects (sibling `refresh*` methods persist) |
| `suggestModels(String modelType)` | `ModelSuggestionService.suggestions(typeFilter, compatFilter)` | read-only | Enum parsing; trim output records to protect context window |
| `searchModelLibrary(String query, int limit)` | `OllamaLibrarySearchService.search(query, limit)` | read-only | Remote scrape — clamp limit, soft-fail on timeout |
| `deleteModel(String modelTag)` | `ModelDashboardService.deleteModel(model)` → `AdminResult` | **destructive** (multi-GB re-download to undo) | Completes pull/list/delete lifecycle |
| `containerMetrics(String instanceName)` | `DbInstanceService.getContainerMetrics(configId)` | read-only | Backed by Docker stats streaming callback (`DockerDeployEngine.getContainerMetrics` ~L705) — verify one-shot prompt return; clear error for non-running |

### Tier 3 — nice-to-have / debatable (user decision before building)

- `renameInstance(name, newName)` — wraps `DbInstanceService.rename`. Write. Renames break the agent's name-based lookups mid-conversation. Cut candidate.
- `syncInstanceStatuses()` — wraps `DbInstanceService.syncStatuses()`. Classification dispute: mutates DB status rows but only reconciles with Docker reality. Recommend read-only (lets read-only MCP clients self-heal stale status); conservative alternative: write.
- `discoverContainers()` — wraps `DbInstanceService.discoverContainers()`. Read-only. Niche without an `importContainer` counterpart.
- `loadModel(tag)` / `unloadModel(tag)` — wrap `ModelDashboardService.load/unload`. Write (VRAM only). Ollama auto-manages keep-alive; marginal.

**Considered and rejected:** `HostMetricsService.snapshot()` / `SystemDbStatsService.getStats()` (overlap `systemInfo`/`containerMetrics`); `listKafkaTopics` via `execCapture` (the exec precedent already strains the thin-wrapper rule).

## 3. Thin-wrapper violations flagged

1. **`importContainer` tool** — composing `ImportRequest` from flat params (type inference, port detection) is judgment logic. Redesign: add `DbInstanceService.importByContainerName(String)` in the service layer first, then wrap. Defer.
2. **Async model pull with progress** — `ModelDashboardService.pullAsync` + `PullState` exist, but poll-for-progress needs correlation/state handling; CLAUDE.md mandates the pipeline SPI (`DeployStep`/`PipelineRunner`) for long-running background ops. Keep blocking `pullModel`; document MCP client timeout risk for large models.
3. **`compareModels`** — `ModelComparisonService.compare(...)` returns `Flux<ServerSentEvent<ComparisonChunk>>`; collecting a stream in a tool is new logic + long block. Out of scope. RAG ingestion likewise stays internal.

## 4. Cross-cutting concerns

- **Split the tool class now.** 9 → ~21 tools with 4+ new constructor deps is too much. Split into `InstanceTools` + `ModelTools` (shared `AgentSafety`). Update BOTH registration sites: `McpServerConfig` → `toolObjects(instanceTools, modelTools)` and `AgentChatService.exposedCallbacks()` likewise.
- **Most important safety deliverable:** a reflection test in `AgentSafetyTest` — `AgentSafety` is keyed by bare method name, so a forgotten entry silently makes a write tool auto-run, and a name collision across split classes breaks policy. Assert: every `@Tool` method is in at most one set; names globally unique across all tool classes.
- **AgentSafety updates:** WRITE += `startInstance` (+ `renameInstance`, `loadModel`, `unloadModel` if kept); DESTRUCTIVE += `deleteModel`.
- **Config gates:** existing `portwrangler.agent.read-only` + `portwrangler.mcp.write-enabled` suffice. Open decision: optional `portwrangler.mcp.destructive-enabled` sub-gate (currently write-enabled also exposes `removeInstance`/`deleteModel` externally).
- **Output discipline:** every list tool maps to compact records (`toSummary` pattern). Avoid `returnDirect=true` on new tools (only `stackSummary` uses it, deliberately).
- **`MAX_TOOL_ROUNDS = 6`** may need raising to 8 for chains like catalog → check tag → deploy → poll. Flag, don't change unilaterally.
- **No Liquibase/schema changes anywhere.** After implementation, run `graphify update .`.

## 5. Implementation sequence

Verification per batch: `./gradlew spotlessApply build`, then targeted `./gradlew test --tests "<class>"`; CI parity `./gradlew build spotlessCheck`.

1. **senior-dev — structural prep:** split tool classes (pure move, zero behavior change), update both wiring sites, add the AgentSafety reflection test. Everything else blocks on this.
2. **implementer (parallel after split) — Tier 1 instance tools:** `startInstance`, `deploymentStatus`, `listCatalog`, `exportCompose`, `systemInfo`, `deployDatabase` description tweak + safety entries + tests.
3. **implementer (parallel after split) — model tools:** `listModels`, `suggestModels`, `searchModelLibrary`, `deleteModel` + safety entries + tests.
4. **implementer — Tier 2 image/metrics tools:** `availableVersions`, `checkImageTag`, `containerMetrics` (verify `checkForDeploy` side effects, metrics latency).
5. **senior-dev review gate:** everything touching MCP exposure + the new write/destructive classifications; confirm `McpServerConfigTest` covers filtering of new names.
6. **User decisions before Tier 3:** rename/sync/discover/load-unload inclusion, `syncInstanceStatuses` classification, `mcp.destructive-enabled` gate, `MAX_TOOL_ROUNDS` bump.

Every worker brief must include: exact file paths, the thin-wrapper rule quoted from `InfrastructureTools` Javadoc, the named service method signatures, the safety-set assignment, graphify-first exploration, and the Spotless note (CI checks but doesn't fix).

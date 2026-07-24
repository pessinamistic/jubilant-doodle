# Remediation Plan: Compose Export UX + Docker/Brew Engine Distinction

> Status: **IMPLEMENTED** (2026-07-19), awaiting merge. Problem 1 on `fix/compose-export-ux` (a0593ed,
> +41daa3e follow-up fix below); Problem 2 on `fix/docker-brew-engine-separation` (17aec2e backend,
> 2891b34 DTO/UI + review fixes).
> Verified: backend 162 tests / 0 failures / 1 skipped (Docker-gated IT); frontend lint at pre-existing
> baseline, build green. Senior review: no blockers; 500→400 exception fix and
> `InstanceResponse`-uses-`effectiveDeployMethod()` nit applied.
> Merge notes: small expected conflict in `frontend/src/pages/InstancesPage.jsx` (toolbar/state block);
> post-merge cosmetic follow-up — hide the detail-page export button for non-Docker instances;
> first backend start applies the idempotent V3 backfill. These fixes unblock the MCP tool expansion
> (`docs/mcp-tools-expansion-plan.md`).
>
> **Post-implementation fix (2026-07-24, commit `41daa3e` on `fix/compose-export-ux`):** live
> verification against the user's real data surfaced a second, more fundamental export bug —
> `exportableContainers()` filtered out any config with `isTemplate()==true`, but
> `ConfigTemplateService.create()` sets that flag on *every* config it creates, including ones
> deployed through the normal UI/agent path (both reuse the same `DeploymentConfig` row). This
> silently dropped almost all normally-deployed instances from export; only imported (non-template)
> instances survived. Fix: removed the `!isTemplate()` filter — it was also redundant, since
> `instanceService.listAll()` already scopes to real `DeployedContainer` rows, so a pure blueprint
> with no deployment never reaches this list regardless of the flag. Kept the `isDockerDeployed`
> filter. Test `excludes_system_template_and_removed_instances` replaced with
> `excludes_system_and_removed_instances` (template case dropped) + new
> `deployed_instance_flagged_as_template_is_still_exported`. Re-verified: 162/0/1 (same baseline).
> Also investigated ground truth on the user's live `kafka` instance (`HOMEBREW`, `version="0.0"`)
> per this doc's original open question: `docker ps -a` shows no kafka container and `brew services
> list` shows it genuinely running as a brew service — the `HOMEBREW` detection is correct, not a
> bug. The `version="0.0"` placeholder is cosmetic only (never reaches a Docker code path for a
> HOMEBREW-tagged config; only appears in display/RAG text) — no code change needed. No other
> `isTemplate()`-based filtering was found elsewhere in the backend (grep-checked).

## Problem 1 — Compose export is whole-stack only, on the wrong page

### Root cause

| Concern | Location |
|---|---|
| Export service, no filtering support | `backend/.../service/ComposeExportService.java:42` — `exportYaml()` takes no args; selection buried in private `exportableContainers()` (L79-90) walking `instanceService.listAll()`, filtered non-REMOVED/non-system/non-template, deduped by config id |
| Single whole-stack endpoint | `backend/.../api/ComposeExportController.java:25` — `GET /export/docker-compose`, no params, attachment download |
| Frontend API call | `frontend/src/api/client.js:104` — `exportDockerCompose()`, no params, blob response |
| Button on the wrong page | `frontend/src/pages/InstancesPage.jsx:76-94` (handler) + L239-243 (button). `InstanceDetailPage.jsx` has no export action |

The service already renders per-container blocks in a loop (`appendService`, L102-150), so id-filtering is a small extension. L52-53 already skips non-Docker-image types; once Problem 2 lands, the filter should also exclude non-`DOCKER` `deployMethod` rows (brew services have no compose representation).

### Design

**Backend**
1. `ComposeExportService.exportYaml(Collection<String> configIds)` — new overload; null/empty = all (existing `exportYaml()` delegates). One extra `.filter` in `exportableContainers()`; also filter `deployMethod == DOCKER || null`.
2. `ComposeExportController` — optional param on the existing endpoint: `GET /export/docker-compose?configIds=a,b,c`. No param = current behavior (backward compatible; no new endpoint). Nicety: single id → filename `<instance-name>-compose.yml`.

**Frontend**
3. `client.js` — `exportDockerCompose(configIds)` appending the query param when present.
4. `InstanceDetailPage.jsx` — export button in header actions calling with `[id]`; extract the blob-download logic from `InstancesPage.jsx:76-94` into a shared util.
5. `InstancesPage.jsx` — button opens new `components/ExportComposeModal.jsx` (imitate `ImportModal.jsx`): checkbox list of active instances (name, type, port, pre-checked all), "Export selected" disabled at zero.

**MCP knock-on**: planned tool becomes `exportCompose(List<String> instanceNames)` — empty = whole stack; resolve names via `requireByName`, call `exportYaml(configIds)`. Still read-only, still thin.

### Tasks
- **P1-A (implementer):** backend overload + controller param + `ComposeExportServiceTest` (subset filter, empty=all, unknown id ignored, non-DOCKER excluded). Verify `./gradlew spotlessApply build` + targeted tests.
- **P1-B (implementer, after P1-A):** frontend — client param, detail-page button, `ExportComposeModal`, shared download util. Contract: `GET /api/export/docker-compose?configIds=<csv>` → `application/x-yaml` attachment. Verify `npm run lint && npm run build`.
- No dedicated senior review (no schema/security surface); folded into P2-D.

## Problem 2 — Docker vs. Brew distinction: EXISTS in the model, but leaky

### Root cause (premise correction)

The distinction already exists in the backend but is partially wired, never surfaced in the UI, and bypassed by several Docker-only paths.

**What works today:**
- `DeployMethod` enum (`model/DeployMethod.java:6`): `DOCKER, HOMEBREW, APT, CHOCOLATEY, WINGET, EMBEDDED`.
- Persisted: nullable `deploy_method VARCHAR(50)` (`V1__baseline.sql:19`), mapped `DeploymentConfig.java:61-62`, exposed via `InstanceResponse.java:25`.
- Set on config creation (`ConfigTemplateService.java:108` → DOCKER) and on import/discovery via the `brew:` synthetic containerId prefix (`DbInstanceService.detectImportMethod` L540-545; applied L366-367, L402/415).
- Correctly routed: `startInstance` (L214), `stopInstance` (L234), `untrackInstance` (L253), `reTrackInstance` (L319-323), `syncStatuses` (L447-462), `getLogs` (L516 — brew advisory string).

**Actual bugs:**
1. `DbInstanceService.getContainerMetrics` (L128-140) — calls Docker with the synthetic `brew:xxx` id; UI shows the Metrics tab for all non-system instances (`InstanceDetailPage.jsx:60,278,907`) → broken metrics for brew.
2. `DbInstanceService.rename` (L497-511) — `docker.renameContainer(...)` unconditional; fails for brew.
3. `DeploymentRecovery.recoverContainers` (`config/DeploymentRecovery.java:65-88`) — non-null containerId asked of Docker; `brew:` id misresolved (low probability, two-line guard).
4. `InfrastructureTools.createKafkaTopic` (L199) — guards only `containerId != null`; synthetic `brew:` id would reach `execCapture`. Should assert DOCKER.
5. **Polarity bug in every existing guard:** checks are `== HOMEBREW`, silently treating APT/CHOCOLATEY/WINGET/EMBEDDED as Docker. Docker-only ops must check `== DOCKER` (null tolerated as legacy DOCKER).
6. **Null semantics duplicated ad hoc** (L319-320, L447-450) instead of centralized.

**UI gaps (what the user actually sees):**
- `InstanceCard.jsx` (L201, 236) — no engine indicator. No engine filter on `InstancesPage`.
- `InstanceDetailPage.jsx` — only raw "Deploy Method" text rows (L1361, 1433, 1595); Metrics/rename not gated.
- `DiscoveredContainerDto` — no source field, so ImportModal can't label brew vs Docker (merged list from `discoverContainers`, `DbInstanceService.java:490`).

### Design

1. **Centralize default:** `DeploymentConfig.effectiveDeployMethod()` (null → DOCKER, documented legacy default); replace scattered ternaries; flip all Docker-only guards to `effectiveDeployMethod() == DOCKER`. No strategy-pattern router yet (2 engines / ~8 sites don't justify it; revisit if APT activates).
2. **Backfill changeset:** `V3__deploy_method_backfill.sql` — `UPDATE deployment_config SET deploy_method='DOCKER' WHERE deploy_method IS NULL AND is_template = FALSE` (worker confirms exact column/flag names against V1). Column stays **nullable** (templates legitimately NULL per V1 L7 comment). Idempotent; the accessor makes the app correct even without it — hygiene, not a correctness dependency. ddl-auto stays `validate`; no entity change.
3. **Guard the four sites:** metrics → `ContainerMetricsResponse.unavailable()` for non-DOCKER; rename → config-only rename for non-DOCKER with log line (brew service names aren't ours); recovery skips non-DOCKER rows; `createKafkaTopic` asserts DOCKER.
4. **DTO:** add `DeployMethod source` to `DiscoveredContainerDto`, populate both discovery branches.
5. **UI:** `EngineBadge` component (imitate `StatusBadge.jsx`) in `InstanceCard`, `InstanceDetailPage` header, ImportModal rows; engine filter chip on `InstancesPage` (imitate showRemoved/showUntracked toggles); hide Metrics tab + disable rename for non-DOCKER with tooltip. `InstanceResponse.deployMethod` already ships to the client — zero new API surface.

### Tasks
- **P2-A (senior-dev):** items 1-3 — accessor, polarity flip across `DbInstanceService`/`DeploymentRecovery`/`InfrastructureTools`, Liquibase backfill, guard tests (extend `DbInstanceServiceTest`). Verify: full `./gradlew test`.
- **P2-B (implementer, after P2-A):** DTO field + populate sites + test.
- **P2-C (implementer, parallel with P2-B):** all frontend surfacing. Contract: `deployMethod`/`source` values are enum names; null = DOCKER. Verify `npm run lint && npm run build`.
- **P2-D (senior-dev):** review P2-B/P2-C + P1 diffs in one pass. Then `graphify update .`.

### Risks
- **Liquibase:** additive UPDATE, no DDL, idempotent; normal new changeset for synced installs.
- **Behavior changes:** brew loses broken metrics view (honest "unavailable"), rename becomes config-only for brew — strict improvements; list in release notes.
- **Polarity flip** alters behavior for hypothetical APT/EMBEDDED rows; no production path creates them today (`detectImportMethod` returns only DOCKER/HOMEBREW) — state explicitly in the P2-A brief + add a test.

## Optional severable phase — Homebrew tap explorer (macOS)

Exists: `BrewDeployEngine.isAvailable()` (L31), `discoverServices` (`brew services list` parsing), start/stop/status, generic `runCommand` (L235-263); `OsDetector.isCommandAvailable` for gating. **Nothing for taps/formula listing/search.**

Scope: new `BrewCatalogService` (`brew tap`, `brew tap-info --json`, `brew search`, `brew info --json=v2` — JSON parsing, caching, timeouts) + `BrewCatalogController` (3 read endpoints) + new frontend page, macOS-gated. Genuinely new service logic: ~1 senior-dev backend task + 1 implementer frontend task. Decide after P1/P2 ship; only shared code is `runCommand` (extract only if approved).

## Sequencing

1. **P2-A first** — fixes real bugs in paths the MCP plan wants to wrap (`containerMetrics`, `renameInstance`, `startInstance`); building tools first would bake the bugs into the MCP surface.
2. **P1-A parallel with P2-A** (different files) — defines the `exportYaml(configIds)` contract the MCP `exportCompose` tool needs.
3. **P1-B, P2-B, P2-C in parallel** (P1-B and P2-C both touch `InstancesPage.jsx` — merge into one frontend task or rebase P2-C after P1-B).
4. **P2-D review gate**, then un-hold the MCP plan with its three amendments (see that doc's amendment section).

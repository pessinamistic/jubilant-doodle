# Ollama Model Management — Gap Analysis & Implementation Plan

> Grounded against the code on `feature/port-wrangler-ai` (audited 2026-07-06).
> Companion to `HANDOVER-port-wrangler-ai.md` and `port-wrangler-future-roadmap.md`.

## 1. TL;DR — what's actually wrong

Model management is ~80% built but feels missing because of **two disconnects**, not absent code:

1. **The Cookbook's "Pull" button is fake.** `ModelCookbookPage.jsx` → `ModelCard.copyPull()`
   only calls `navigator.clipboard.writeText('ollama pull <tag>')`. The page most users treat as
   "models" copies a terminal command instead of pulling anything. It never touches the backend.
2. **Discovery and management live on separate, unlinked pages.** `/models` (Cookbook = "what can I
   run") and `/runtime` (Runtime = real pull/run/pause/delete) don't reference each other's actions.
   After deploying an Ollama container you must independently discover that `/runtime` exists and
   type a raw tag to pull.

Everything below the UI is real and working:

- `ModelRuntimeController` (`/models/runtime`) → dashboard, `/load`, `/unload`, `/delete`,
  `/settings`, `/pull` — all live.
- `ModelDashboardService` → lists on-disk + in-memory models, syncs to `pulled_model`, per-model
  settings, async pulls on virtual threads.
- `OllamaAdminClient` / `OllamaModelPuller` → real Ollama HTTP API (`/api/tags`, `/api/ps`,
  `/api/generate`, `/api/delete`, `/api/pull`).
- `InfrastructureTools.pullModel` → agent tool **is implemented** (handover §6b is stale on this).

So the plan is mostly **wiring + UX**, with one genuinely new capability worth adding (streaming
pull progress).

## 2. Confirmed gaps (code-grounded)

| # | Gap | Evidence | Severity |
|---|-----|----------|----------|
| G1 | Cookbook "Pull" copies CLI text, doesn't pull | `ModelCookbookPage.jsx:158-163` `copyPull` uses clipboard only | **High** — this is the "no UI" complaint |
| G2 | Cookbook and Runtime are unlinked; no "pull into my runtime" from a suggestion | `ModelCookbookPage` imports only `getModelSuggestions`/`getSystemProfile` | **High** |
| G3 | Pulls have no live progress | `OllamaModelPuller.pull` uses `stream:false`, blocks ≤30 min; dashboard `pulls` map only holds `"pulling"`/`"failed"` | **Medium** |
| G4 | No runtime-reachability / "deploy Ollama first" guidance on the Cookbook | Cookbook never checks the runtime dashboard | **Medium** |
| G5 | Runtime pull is free-text only (no browse/validate of tags) | `RuntimePage.jsx` pull input | **Low** |
| G6 | Agent `pullModel` blocks the tool loop with no progress feedback | `InfrastructureTools.pullModel` calls blocking `pull` | **Low** |

## 3. Design principles (keep the existing architecture)

- **One source of truth for management: `ModelDashboardService`.** The Cookbook should *call* it,
  not grow its own pull path. Reuse `pullAsync`.
- **Reuse `OllamaAdminClient`/`OllamaModelPuller`** — no new HTTP client, matches handover §4
  "verify-before-build" and the JDK-`HttpClient`-only convention.
- **Servlet SSE via `Flux<ServerSentEvent>`** for progress, mirroring `ChatController` /
  `AgentChatController` (no WebFlux — handover §4 hard constraint).
- **Liquibase only** if any schema changes (none required for Phase 1–2).

## 4. Plan (phased)

### Phase 1 — Make the Cookbook actually manage models  *(highest value, ~0.5 day)*

Goal: the button users already click does the right thing, and links the two surfaces.

Backend: none required (reuse `/models/runtime/pull` and `/models/runtime`).

Frontend (`ModelCookbookPage.jsx`):
1. Fetch the runtime dashboard on load (`getRuntimeDashboard`) to know: is a runtime reachable, and
   which tags are already on disk.
2. Replace `copyPull` with a real action:
   - If runtime reachable → `pullRuntimeModel(model.ollamaTag)`, toast "Pulling…", and reflect the
     in-flight state from the dashboard `pulls` map.
   - Card states: **Pull** → **Pulling…** (spinner, from `pulls`) → **On disk / Run** (once it
     appears in `dashboard.models`).
   - Keep "Copy `ollama pull`" as a secondary affordance for native-CLI users.
   - If no reachable runtime → button becomes **"Deploy Ollama first"** linking to
     `/deploy?tool=OLLAMA` (route already supports the `tool` query param).
3. Add a small runtime-status chip to the Cookbook header (reachable / managed instance name),
   linking to `/runtime`.

Acceptance: from `/models`, clicking Pull on a compatible model pulls it into the running Ollama and
the card converges to "on disk"; with no runtime, the button routes to deploy.

### Phase 2 — Streaming pull progress  *(the one real new capability, ~1 day)*

Goal: replace "pulling…/failed" with live percentage, so multi-GB pulls are legible in both pages
and the agent.

Backend:
1. `OllamaModelPuller`: add `pullStreaming(baseUrl, tag, Consumer<PullProgress> onEvent)` that posts
   `{"name":tag,"stream":true}` and parses newline-delimited JSON (`status`, `digest`, `completed`,
   `total`). Keep the existing `stream:false` `pull` for the agent's blocking path.
   - Add a pure, unit-testable `parseProgressLine(String) -> PullProgress` (matches the existing
     `buildPullBody` test style).
2. `ModelDashboardService`: track richer per-tag progress (percent + phase) in a
   `Map<String, PullProgress>` instead of a bare status string; keep the `pulls` map shape
   backward-compatible or bump the dashboard DTO.
3. New endpoint `GET /models/runtime/pull/stream?model=<tag>` returning
   `Flux<ServerSentEvent<PullProgress>>` (pattern: `AgentChatController`). Events: `progress`,
   `done`, `pull_error` (avoid the reserved `error` name — handover gotcha §7.2).

Frontend:
- Runtime + Cookbook pull rows subscribe via `EventSource`, render a progress bar (`completed/total`),
  close on `done`/`pull_error`. Fall back to the existing 15s dashboard poll if SSE drops.

Acceptance: pulling `llama3.1:8b` shows a moving progress bar to completion in both pages.

### Phase 3 — Unify discovery + management  *(polish, ~0.5 day)*

- Fold the Cookbook's hardware-scored suggestions into the Runtime page's pull box as an
  autocomplete/browse list (reuse `getModelSuggestions`), so the free-text tag input (G5) gains
  validated choices.
- Or, simpler: add a "Manage in Runtime" link from each Cookbook card once a model is on disk.
- Cross-link nav: Cookbook ↔ Runtime.

### Phase 4 — Agent progress (optional, ~0.5 day)

- Have `InfrastructureTools.pullModel` emit progress through the same `ModelDashboardService`
  progress map so an in-flight agent pull is visible in the Runtime UI, and the agent returns a
  concise final status. Keep it confirmation-gated (already classified WRITE in `AgentSafety`).

## 5. Files that will change

- `frontend/src/pages/ModelCookbookPage.jsx` — Phase 1 (real pull), Phase 3 (links)
- `frontend/src/pages/RuntimePage.jsx` — Phase 2 (progress bar), Phase 3 (browse)
- `frontend/src/api/client.js` — add `pullRuntimeModelStream` (SSE) if Phase 2
- `backend/.../runtime/OllamaModelPuller.java` — Phase 2 streaming
- `backend/.../runtime/ModelDashboardService.java` — Phase 2 progress model
- `backend/.../api/ModelRuntimeController.java` — Phase 2 SSE endpoint
- `backend/.../ai/tools/InfrastructureTools.java` — Phase 4 (optional)

No schema/Liquibase changes for Phases 1–3.

## 6. Testing

- Backend unit: `parseProgressLine` (pure), `pullStreaming` against a stubbed `HttpClient`/mock
  server; dashboard progress-map transitions. SSE endpoint via MockMvc `asyncDispatch` (handover §7.8).
- Frontend: `eslint` clean + `npm run build`; manual smoke — pull from Cookbook, watch progress,
  confirm card converges; no-runtime path routes to deploy.
- Regression: existing `/models/runtime` unit tests stay green; agent tool tests unaffected.

## 7. Suggested order

Phase 1 → verify end-to-end (this alone resolves the "no UI/API" complaint) → Phase 2 (progress) →
Phase 3/4 as polish. Each phase is independently shippable and merges `--no-ff` into
`feature/port-wrangler-ai` per handover conventions.

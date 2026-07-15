#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Port Wrangler — pull latest master and redeploy
#
#   ./update.sh            pull origin/master; rebuild only if there are new commits
#   ./update.sh --force    rebuild & restart even with no new commits
#
# The Docker image build compiles both the React frontend and the Spring Boot
# backend (multi-stage Dockerfile), so one run updates the UI and the API.
# Data is safe: the system-DB volume and ~/.db-deployer are untouched.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

BRANCH="master"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILES=(-f "$REPO_DIR/docker-compose.yml" -f "$REPO_DIR/docker-compose.proxy.yml")

# Optional overrides — see .env.example (compose auto-loads .env as well).
if [[ -f "$REPO_DIR/.env" ]]; then
  set -a; source "$REPO_DIR/.env"; set +a
fi
DOMAIN="${PORTWRANGLER_DOMAIN:-portwrangler.local}"
HEALTH_URL="http://localhost:${API_PORT:-8590}/api/v3/api-docs"

FORCE=false
[[ "${1:-}" == "--force" || "${1:-}" == "-f" ]] && FORCE=true

BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; RESET=$'\033[0m'
step() { printf '\n%s==> %s%s\n' "$BOLD" "$1" "$RESET"; }
ok()   { printf '%s  ✔ %s%s\n' "$GREEN" "$1" "$RESET"; }
warn() { printf '%s  ! %s%s\n' "$YELLOW" "$1" "$RESET"; }
die()  { printf '%s  ✖ %s%s\n' "$RED" "$1" "$RESET"; exit 1; }

cd "$REPO_DIR"

# ── 0. Sanity checks ─────────────────────────────────────────────────────────
docker info >/dev/null 2>&1 || die "Docker daemon isn't running. Run ./start_macos_setup.sh first."
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "Not a git repository: $REPO_DIR"

# Colima socket (same logic as setup script — compose needs it every run).
if [[ "$(docker context show 2>/dev/null)" == "colima" || ( -S "$HOME/.colima/default/docker.sock" && ! -S /var/run/docker.sock ) ]]; then
  export DOCKER_SOCKET="$HOME/.colima/default/docker.sock"
fi

# ── 1. Fetch & compare ───────────────────────────────────────────────────────
step "Checking $BRANCH for new commits"

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "$BRANCH" ]]; then
  die "You are on branch '$CURRENT_BRANCH', not '$BRANCH'. Switch first: git checkout $BRANCH"
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  warn "You have uncommitted local changes. They will NOT be discarded,"
  warn "but the pull may fail if they conflict with incoming commits."
fi

git fetch origin "$BRANCH"

LOCAL_SHA="$(git rev-parse HEAD)"
REMOTE_SHA="$(git rev-parse "origin/$BRANCH")"

if [[ "$LOCAL_SHA" == "$REMOTE_SHA" && "$FORCE" == false ]]; then
  ok "Already up to date with origin/$BRANCH ($(git rev-parse --short HEAD))"
  echo "  Use ./update.sh --force to rebuild anyway."
  exit 0
fi

if [[ "$LOCAL_SHA" != "$REMOTE_SHA" ]]; then
  echo
  echo "  Incoming changes:"
  git log --oneline --no-decorate "HEAD..origin/$BRANCH" | sed 's/^/    /'
  echo
  git pull --ff-only origin "$BRANCH" \
    || die "Fast-forward pull failed (diverged history or conflicting local changes). Resolve manually, then re-run."
  ok "Pulled $(git rev-parse --short "$REMOTE_SHA")"
else
  warn "No new commits — forcing rebuild (--force)"
fi

# ── 2. Rebuild & redeploy ────────────────────────────────────────────────────
step "Rebuilding image (frontend + backend) and restarting"

docker compose "${COMPOSE_FILES[@]}" up --build -d

# Clean up dangling image layers left behind by the rebuild.
docker image prune -f >/dev/null 2>&1 || true

# ── 3. Health check ──────────────────────────────────────────────────────────
step "Waiting for the app to come back"

printf '  waiting for API'
for _ in $(seq 1 90); do
  if curl -sf -o /dev/null "$HEALTH_URL"; then
    echo; ok "API is healthy"
    break
  fi
  printf '.'; sleep 2
done
if ! curl -sf -o /dev/null "$HEALTH_URL"; then
  echo
  warn "App didn't respond within 3 minutes. Check logs with:"
  warn "  docker compose ${COMPOSE_FILES[*]} logs -f app"
  exit 1
fi

step "Deployed $(git rev-parse --short HEAD)"
ok "UI + backend updated: http://$DOMAIN"

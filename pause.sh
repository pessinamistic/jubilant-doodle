#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Port Wrangler — pause the whole setup
#
#   ./pause.sh
#
# Stops everything Port Wrangler runs — the app stack (frontend + app + Caddy),
# the system DB, and every RUNNING managed database container — without
# removing anything. Containers, images, and data all stay in place, and the
# set of running databases is remembered so ./resume.sh brings the setup back
# exactly as it was. Imported containers (created outside Port Wrangler) are
# never touched.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILES=(-f "$REPO_DIR/docker-compose.yml" -f "$REPO_DIR/docker-compose.proxy.yml")

# Optional overrides — see .env.example (compose auto-loads .env as well).
if [[ -f "$REPO_DIR/.env" ]]; then
  set -a; source "$REPO_DIR/.env"; set +a
fi

SYSTEM_DB_CONTAINER="${DBDEPLOYER_SYSTEM_DB_CONTAINER_NAME:-dbdeployer-system-db}"
# Managed instances stopped by pause are remembered here so resume restores
# exactly the set that was running. Lives with the rest of the app's data.
STATE_FILE="$HOME/.db-deployer/paused-instances"

BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; RESET=$'\033[0m'
step() { printf '\n%s==> %s%s\n' "$BOLD" "$1" "$RESET"; }
ok()   { printf '%s  ✔ %s%s\n' "$GREEN" "$1" "$RESET"; }
warn() { printf '%s  ! %s%s\n' "$YELLOW" "$1" "$RESET"; }
die()  { printf '%s  ✖ %s%s\n' "$RED" "$1" "$RESET"; exit 1; }

docker info >/dev/null 2>&1 || die "Docker daemon isn't running — nothing to pause."

# Colima socket (same logic as the other scripts — compose needs it every run).
if [[ "$(docker context show 2>/dev/null)" == "colima" || ( -S "$HOME/.colima/default/docker.sock" && ! -S /var/run/docker.sock ) ]]; then
  export DOCKER_SOCKET="$HOME/.colima/default/docker.sock"
fi

# ── 1. Remember which managed databases are running ──────────────────────────
step "Recording running managed databases"

# Deployed instances are named dbdeployer-<name>-<uuid8>; exclude the stack's
# own containers. Imported containers keep their original names — not matched.
RUNNING="$(docker ps --format '{{.Names}}' | grep -E '^dbdeployer-' \
  | grep -v -E "^(dbdeployer-app|dbdeployer-frontend|${SYSTEM_DB_CONTAINER})$" || true)"

if [[ -n "$RUNNING" ]]; then
  mkdir -p "$(dirname "$STATE_FILE")"
  printf '%s\n' "$RUNNING" > "$STATE_FILE"
  ok "recorded for resume: $(echo "$RUNNING" | tr '\n' ' ')"
elif [[ -f "$STATE_FILE" ]]; then
  # Paused twice in a row — keep the original list instead of clearing it.
  ok "none running — keeping the earlier pause list"
else
  ok "none running"
fi

# ── 2. Stop the app stack ────────────────────────────────────────────────────
step "Stopping the app stack (frontend + app + Caddy)"
docker compose "${COMPOSE_FILES[@]}" stop
ok "stack stopped"

# ── 3. Stop the system DB and managed databases ──────────────────────────────
step "Stopping the system DB and managed databases"

docker stop "$SYSTEM_DB_CONTAINER" >/dev/null 2>&1 \
  && ok "stopped $SYSTEM_DB_CONTAINER" \
  || ok "system DB not running"

if [[ -n "$RUNNING" ]]; then
  while IFS= read -r name; do
    docker stop "$name" >/dev/null && ok "stopped $name"
  done <<< "$RUNNING"
fi

step "Paused"
ok "Nothing was removed — bring everything back with:  ./resume.sh"

#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Port Wrangler — resume a paused setup
#
#   ./resume.sh
#
# Counterpart to ./pause.sh: starts the Docker daemon if needed, then the
# system DB, the app stack (frontend + app + Caddy), and every managed
# database container that was running when you paused. No rebuild, no data
# changes — containers are started, not recreated.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILES=(-f "$REPO_DIR/docker-compose.yml" -f "$REPO_DIR/docker-compose.proxy.yml")

# Optional overrides — see .env.example (compose auto-loads .env as well).
if [[ -f "$REPO_DIR/.env" ]]; then
  set -a; source "$REPO_DIR/.env"; set +a
fi
DOMAIN="${PORTWRANGLER_DOMAIN:-portwrangler.local}"
HTTP_PORT="${HTTP_PORT:-80}"
API_PORT="${API_PORT:-8590}"
BASE_URL="http://$DOMAIN$([[ "$HTTP_PORT" != "80" ]] && echo ":$HTTP_PORT" || true)"
HEALTH_URL="http://localhost:${API_PORT}/api/v3/api-docs"

SYSTEM_DB_CONTAINER="${DBDEPLOYER_SYSTEM_DB_CONTAINER_NAME:-dbdeployer-system-db}"
STATE_FILE="$HOME/.db-deployer/paused-instances"

BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; RESET=$'\033[0m'
step() { printf '\n%s==> %s%s\n' "$BOLD" "$1" "$RESET"; }
ok()   { printf '%s  ✔ %s%s\n' "$GREEN" "$1" "$RESET"; }
warn() { printf '%s  ! %s%s\n' "$YELLOW" "$1" "$RESET"; }
die()  { printf '%s  ✖ %s%s\n' "$RED" "$1" "$RESET"; exit 1; }

# ── 1. Docker daemon (may be down after a pause + reboot) ────────────────────
step "Ensuring the Docker daemon is running"

if ! docker info >/dev/null 2>&1; then
  if command -v colima >/dev/null; then
    warn "Daemon not running — starting Colima..."
    colima start
  elif [[ -d "/Applications/Docker.app" ]]; then
    warn "Daemon not running — starting Docker Desktop..."
    open -a Docker
  else
    die "No Docker daemon found. Start Docker Desktop or Colima, then re-run."
  fi
  printf '  waiting for daemon'
  for _ in $(seq 1 60); do
    docker info >/dev/null 2>&1 && break
    printf '.'; sleep 2
  done
  echo
  docker info >/dev/null 2>&1 || die "Docker daemon did not come up within 120s."
fi
ok "Docker daemon is up"

# Colima socket (same logic as the other scripts — compose needs it every run).
if [[ "$(docker context show 2>/dev/null)" == "colima" || ( -S "$HOME/.colima/default/docker.sock" && ! -S /var/run/docker.sock ) ]]; then
  export DOCKER_SOCKET="$HOME/.colima/default/docker.sock"
fi

# ── 2. System DB first ───────────────────────────────────────────────────────
# The app would start it anyway (SystemDbProvisioner) — starting it up front
# just makes the app boot faster.
step "Starting the system DB"
docker start "$SYSTEM_DB_CONTAINER" >/dev/null 2>&1 \
  && ok "started $SYSTEM_DB_CONTAINER" \
  || ok "no existing system-DB container — the app will provision it"

# ── 3. App stack ─────────────────────────────────────────────────────────────
step "Starting the app stack (frontend + app + Caddy)"
docker compose "${COMPOSE_FILES[@]}" up -d
ok "stack started"

# ── 4. Managed databases from the pause list ─────────────────────────────────
if [[ -f "$STATE_FILE" ]]; then
  step "Restarting managed databases from the pause list"
  while IFS= read -r name; do
    [[ -z "$name" ]] && continue
    docker start "$name" >/dev/null 2>&1 \
      && ok "started $name" \
      || warn "could not start $name (removed since pause?)"
  done < "$STATE_FILE"
  rm -f "$STATE_FILE"
fi

# ── 5. Health check ──────────────────────────────────────────────────────────
step "Waiting for the app"

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

step "Resumed"
ok "UI is back at:  $BASE_URL"

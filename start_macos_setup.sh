#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Port Wrangler — one-shot macOS setup
#
#   ./start_macos_setup.sh
#
# What it does:
#   1. Verifies prerequisites (macOS, git, Docker) and starts the Docker
#      daemon if needed (Docker Desktop or Colima — auto-detected).
#   2. Adds "127.0.0.1 portwrangler.local" to /etc/hosts (asks for sudo once).
#   3. Builds & starts the stack (frontend + app + Caddy reverse proxy) via
#      Docker Compose. On first boot the app auto-provisions its pgvector
#      system DB as a sibling container (dbdeployer-system-db, port 5499).
#      Ports/domain are overridable via a .env file (see .env.example).
#   4. Waits until the API is healthy, then opens http://portwrangler.local
#
# Re-runnable: every step is idempotent. Run it again any time.
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
UI_PORT="${UI_PORT:-8591}"
BASE_URL="http://$DOMAIN$([[ "$HTTP_PORT" != "80" ]] && echo ":$HTTP_PORT" || true)"
HEALTH_URL="http://localhost:${API_PORT}/api/v3/api-docs"

# ── Pretty output ────────────────────────────────────────────────────────────
BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; RESET=$'\033[0m'
step() { printf '\n%s==> %s%s\n' "$BOLD" "$1" "$RESET"; }
ok()   { printf '%s  ✔ %s%s\n' "$GREEN" "$1" "$RESET"; }
warn() { printf '%s  ! %s%s\n' "$YELLOW" "$1" "$RESET"; }
die()  { printf '%s  ✖ %s%s\n' "$RED" "$1" "$RESET"; exit 1; }

# ── 1. Prerequisites ─────────────────────────────────────────────────────────
step "Checking prerequisites"

[[ "$(uname -s)" == "Darwin" ]] || die "This script is for macOS only."
command -v git >/dev/null || die "git not found. Install Xcode Command Line Tools: xcode-select --install"
[[ -f "$REPO_DIR/docker-compose.yml" ]] || die "docker-compose.yml not found next to this script."

if ! command -v docker >/dev/null; then
  warn "Docker CLI not found."
  if command -v brew >/dev/null; then
    read -r -p "  Install Docker Desktop via Homebrew now? [y/N] " reply
    if [[ "$reply" =~ ^[Yy]$ ]]; then
      brew install --cask docker
    else
      die "Install Docker Desktop (https://docker.com) or Colima, then re-run."
    fi
  else
    die "Install Docker Desktop (https://docker.com) or Colima, then re-run."
  fi
fi
ok "git + docker present"

# ── 2. Start the Docker daemon if it isn't running ───────────────────────────
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

# Colima keeps its socket in ~/.colima — the compose file mounts $DOCKER_SOCKET
# into the app container so Port Wrangler can manage sibling containers.
if [[ "$(docker context show 2>/dev/null)" == "colima" || ( -S "$HOME/.colima/default/docker.sock" && ! -S /var/run/docker.sock ) ]]; then
  export DOCKER_SOCKET="$HOME/.colima/default/docker.sock"
  ok "Colima detected — DOCKER_SOCKET=$DOCKER_SOCKET"
fi

# ── 3. Local domain: /etc/hosts entry ────────────────────────────────────────
step "Setting up local domain ($DOMAIN)"

if grep -qE "^[^#]*[[:space:]]$DOMAIN([[:space:]]|\$)" /etc/hosts; then
  ok "/etc/hosts entry already present"
else
  warn "Adding '127.0.0.1 $DOMAIN' to /etc/hosts (sudo password may be required)"
  printf '127.0.0.1\t%s\n' "$DOMAIN" | sudo tee -a /etc/hosts >/dev/null
  ok "hosts entry added"
fi

# Flush DNS caches so the new name resolves immediately.
sudo dscacheutil -flushcache 2>/dev/null || true
sudo killall -HUP mDNSResponder 2>/dev/null || true

# ── 4. Build & start the stack ───────────────────────────────────────────────
step "Building and starting Port Wrangler (this can take a few minutes on first run)"

docker compose "${COMPOSE_FILES[@]}" up --build -d

# ── 5. Wait for the API to be healthy ────────────────────────────────────────
step "Waiting for the app to become healthy"

# First boot also pulls the pgvector system-DB image inside the app's startup,
# so allow up to 5 minutes.
printf '  waiting for API'
for _ in $(seq 1 150); do
  if curl -sf -o /dev/null "$HEALTH_URL"; then
    echo; ok "API is up"
    break
  fi
  printf '.'; sleep 2
done
if ! curl -sf -o /dev/null "$HEALTH_URL"; then
  echo
  warn "App didn't respond within 5 minutes. Check logs with:"
  warn "  docker compose ${COMPOSE_FILES[*]} logs -f app"
  exit 1
fi

# ── 6. Done ──────────────────────────────────────────────────────────────────
step "All set!"
ok "UI:          $BASE_URL"
ok "API:         $BASE_URL/api"
ok "Swagger:     $BASE_URL/api/swagger-ui/index.html"
ok "Direct UI:   http://localhost:${UI_PORT}   (bypasses the proxy)"
ok "Direct API:  http://localhost:${API_PORT}/api   (host port 8080 stays free)"
echo
echo "  To pull the latest master and redeploy later:  ./update.sh"
echo "  To pause everything (keep it all in place):    ./pause.sh   (resume: ./resume.sh)"
echo "  To stop and remove the stack:                  ./cleanup.sh"

open "$BASE_URL" 2>/dev/null || true

#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Port Wrangler — clean up the compose stack
#
#   ./cleanup.sh              stop & remove the app stack (app + Caddy) and
#                             stop the system-DB container. All data survives
#                             — safe to re-run ./start_macos_setup.sh after.
#   ./cleanup.sh --all        FULL reset: also removes the system-DB container,
#                             every dbdeployer-* managed database container,
#                             ~/.db-deployer (ALL metadata + managed DB data!),
#                             the built app image, and the portwrangler.local
#                             /etc/hosts entry.
#   ./cleanup.sh --all --yes  full reset without the confirmation prompt.
#
# Containers imported into Port Wrangler (created outside it) keep their
# original names and are never touched.
# To reset only the system DB (keeping deployed databases), use
# backend/scripts/reset-system-db.sh instead.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILES=(-f "$REPO_DIR/docker-compose.yml" -f "$REPO_DIR/docker-compose.proxy.yml")

# Optional overrides — see .env.example (compose auto-loads .env as well).
if [[ -f "$REPO_DIR/.env" ]]; then
  set -a; source "$REPO_DIR/.env"; set +a
fi
DOMAIN="${PORTWRANGLER_DOMAIN:-portwrangler.local}"

# Same container-name convention as SystemDbProvisioner / reset-system-db.sh.
# The system DB is auto-provisioned by the app as a sibling container — it is
# NOT part of the compose stack, so `compose down` never touches it.
SYSTEM_DB_CONTAINER="${DBDEPLOYER_SYSTEM_DB_CONTAINER_NAME:-dbdeployer-system-db}"
# Parent of all Port Wrangler data: system-DB data dir + managed DB volumes.
DATA_DIR="$HOME/.db-deployer"

ALL=false
YES=false
for arg in "$@"; do
  case "$arg" in
    --all|-a)  ALL=true ;;
    --yes|-y)  YES=true ;;
    -h|--help) sed -n '2,19p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $arg (see --help)"; exit 1 ;;
  esac
done

BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; RESET=$'\033[0m'
step() { printf '\n%s==> %s%s\n' "$BOLD" "$1" "$RESET"; }
ok()   { printf '%s  ✔ %s%s\n' "$GREEN" "$1" "$RESET"; }
warn() { printf '%s  ! %s%s\n' "$YELLOW" "$1" "$RESET"; }
die()  { printf '%s  ✖ %s%s\n' "$RED" "$1" "$RESET"; exit 1; }

# ── 0. Sanity checks ─────────────────────────────────────────────────────────
docker info >/dev/null 2>&1 || die "Docker daemon isn't running — nothing to clean up (start it to remove containers)."

# Colima socket (same logic as the other scripts — compose needs it every run).
if [[ "$(docker context show 2>/dev/null)" == "colima" || ( -S "$HOME/.colima/default/docker.sock" && ! -S /var/run/docker.sock ) ]]; then
  export DOCKER_SOCKET="$HOME/.colima/default/docker.sock"
fi

# ── 1. Full-reset confirmation ───────────────────────────────────────────────
if [[ "$ALL" == true ]]; then
  step "FULL reset — this will permanently delete:"
  warn "the app stack (app + Caddy) and its built image"
  warn "the system-DB container ($SYSTEM_DB_CONTAINER)"
  warn "every managed database container named dbdeployer-*"
  warn "$DATA_DIR (instance metadata, chat history, RAG vectors, ALL managed DB data)"
  warn "the '$DOMAIN' entry in /etc/hosts"
  if [[ "$YES" == false ]]; then
    read -r -p "  Proceed? [y/N] " reply
    [[ "$reply" =~ ^[Yy]$ ]] || die "Aborted — nothing was removed."
  fi
fi

# ── 2. Tear down the compose stack ───────────────────────────────────────────
step "Stopping and removing the app stack"

if [[ "$ALL" == true ]]; then
  # -v also drops the legacy db-data volume on stacks created before the
  # system DB became auto-provisioned; --rmi local removes the built image.
  docker compose "${COMPOSE_FILES[@]}" down --remove-orphans -v --rmi local
else
  docker compose "${COMPOSE_FILES[@]}" down --remove-orphans
fi
ok "Stack removed"

if [[ "$ALL" == false ]]; then
  docker stop "$SYSTEM_DB_CONTAINER" >/dev/null 2>&1 \
    && ok "stopped $SYSTEM_DB_CONTAINER (data kept)" \
    || ok "system-DB container not running"
  step "Done — data kept"
  ok "System-DB data, managed databases, and $DATA_DIR are untouched."
  echo "  Re-deploy any time:  ./start_macos_setup.sh"
  echo "  Full reset instead:  ./cleanup.sh --all"
  exit 0
fi

# ── 3. Remove system-DB + managed database containers ────────────────────────
step "Removing system-DB and managed database containers (dbdeployer-*)"

# May have a non-default name via env override, so remove it explicitly first.
docker rm -f "$SYSTEM_DB_CONTAINER" >/dev/null 2>&1 && ok "removed $SYSTEM_DB_CONTAINER" || true

# Deployed instances are named dbdeployer-<name>-<uuid8> (DockerDeployEngine).
# The compose stack's own containers were already removed by `down` above;
# anything still matching the prefix is fair game in a full reset. Imported
# containers keep their own names and are never matched here.
MANAGED="$(docker ps -a --format '{{.Names}}' | grep -E '^dbdeployer-' || true)"

if [[ -n "$MANAGED" ]]; then
  while IFS= read -r name; do
    docker rm -f "$name" >/dev/null && ok "removed $name"
  done <<< "$MANAGED"
else
  ok "no managed containers found"
fi

# Legacy named volume from stacks created before the system DB became
# auto-provisioned (compose no longer declares it, so `down -v` misses it).
PROJECT_NAME="$(basename "$REPO_DIR" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9_-')"
docker volume rm "${PROJECT_NAME}_db-data" >/dev/null 2>&1 && ok "removed legacy db-data volume" || true

# ── 4. Delete the data directory ─────────────────────────────────────────────
step "Deleting $DATA_DIR"
rm -rf "$DATA_DIR"
ok "data directory removed"

# ── 5. Remove the /etc/hosts entry ───────────────────────────────────────────
step "Removing '$DOMAIN' from /etc/hosts"

if grep -qE "[[:space:]]$DOMAIN([[:space:]]|\$)" /etc/hosts; then
  warn "sudo password may be required"
  DOMAIN_RE="${DOMAIN//./\\.}"
  sudo sed -i '' -E "/[[:space:]]${DOMAIN_RE}([[:space:]]|\$)/d" /etc/hosts
  sudo dscacheutil -flushcache 2>/dev/null || true
  sudo killall -HUP mDNSResponder 2>/dev/null || true
  ok "hosts entry removed"
else
  ok "no hosts entry present"
fi

step "Full reset complete"
ok "Start fresh any time:  ./start_macos_setup.sh"

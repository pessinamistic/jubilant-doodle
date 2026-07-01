#!/usr/bin/env bash
# Wipes the Port Wrangler system DB container and its bind-mounted data directory,
# so the next `bootRun` reinitializes Postgres from scratch (fresh Liquibase run,
# no stale changelog lock, no leftover collation/version mismatch from a prior image).
set -euo pipefail

CONTAINER_NAME="${DBDEPLOYER_SYSTEM_DB_CONTAINER_NAME:-dbdeployer-system-db}"
DATA_DIR="${DBDEPLOYER_SYSTEM_DB_DATA_DIR:-$HOME/.db-deployer/system-db}"

echo "Stopping and removing container: ${CONTAINER_NAME}"
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || echo "  (no existing container)"

echo "Deleting data directory: ${DATA_DIR}"
rm -rf "${DATA_DIR}"

echo "Done. Next 'bootRun' will recreate the container and run the baseline migration fresh."

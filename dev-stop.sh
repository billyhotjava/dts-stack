#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if docker compose version >/dev/null 2>&1; then
  compose_cmd=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose_cmd=(docker-compose)
else
  echo "[dev-stop] ERROR: docker compose not found" >&2
  exit 1
fi

# Load envs if present to avoid compose warnings on missing variables
if [[ -f ./.env ]]; then set -a; source ./.env; set +a; fi
if [[ -f ./.env.dts-source ]]; then set -a; source ./.env.dts-source; set +a; fi

# Fill missing optional PG triplets to avoid compose interpolation warnings on stop
set -a
: "${PG_DB_DTADMIN:=dts_admin}"
: "${PG_USER_DTADMIN:=dts_admin}"
: "${PG_PWD_DTADMIN:=dts_admin}"
: "${PG_DB_AIRBYTE:=airbyte}"
: "${PG_USER_AIRBYTE:=airbyte}"
: "${PG_PWD_AIRBYTE:=airbyte}"
: "${PG_DB_OM:=openmetadata}"
: "${PG_USER_OM:=openmetadata}"
: "${PG_PWD_OM:=openmetadata}"
: "${PG_DB_TEMPORAL:=temporal}"
: "${PG_USER_TEMPORAL:=temporal}"
: "${PG_PWD_TEMPORAL:=temporal}"
set +a

services=(
  dts-admin
  dts-platform
  dts-admin-webapp
  dts-platform-webapp
  dts-public-api
)

echo "[dev-stop] Stopping dts-source dev services (core stack stays running) ..."
"${compose_cmd[@]}" -f docker-compose.yml -f docker-compose.dts-source.yml \
  stop "${services[@]}"

echo "[dev-stop] Done. Restart with: ./dev-up.sh"

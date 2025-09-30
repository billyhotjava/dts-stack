#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if docker compose version >/dev/null 2>&1; then
  compose_cmd=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose_cmd=(docker-compose)
else
  echo "[local-stop] ERROR: docker compose not found" >&2
  exit 1
fi

# Load envs if present to avoid compose warnings on stop
if [[ -f ./.env ]]; then set -a; source ./.env; set +a; fi
if [[ -f ./.env.dts-source ]]; then set -a; source ./.env.dts-source; set +a; fi

# Ensure optional vars exist
set -a
: "${PG_DB_DTADMIN:=dts_admin}"
: "${PG_USER_DTADMIN:=dts_admin}"
: "${PG_PWD_DTADMIN:=dts_admin}"
: "${PG_DB_DTPS:=dts_platform}"
: "${PG_USER_DTPS:=dts_platform}"
: "${PG_PWD_DTPS:=dts_platform}"
set +a

services=(
  dts-admin
  dts-platform
  dts-admin-webapp
  dts-platform-webapp
  dts-public-api
)

echo "[local-stop] Stopping local-dev services (core stack stays running) ..."
"${compose_cmd[@]}" -f docker-compose.yml -f docker-compose.local-dev.yml \
  stop "${services[@]}"

echo "[local-stop] Done. Restart with: ./local-up.sh"

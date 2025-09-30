#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

MODE="images"  # images | local

usage(){ echo "Usage: $0 [--mode images|local]"; }

while (($#)); do
  case "$1" in
    --mode) shift; MODE="${1:-images}";;
    -h|--help) usage; exit 0;;
    *) echo "[dev-stop] Unknown arg: $1" >&2; usage; exit 1;;
  esac
  shift
done

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
set +a

services=(dts-admin dts-platform dts-admin-webapp dts-platform-webapp)

if [[ "$MODE" == "local" ]]; then
  echo "[dev-stop] Stopping local-dev services (core stack stays running) ..."
  "${compose_cmd[@]}" -f docker-compose.yml -f docker-compose.dev.yml stop "${services[@]}"
else
  echo "[dev-stop] Stopping dts-source dev services (core stack stays running) ..."
  "${compose_cmd[@]}" -f docker-compose.yml -f docker-compose-app.yml stop "${services[@]}"
fi

echo "[dev-stop] Done. Restart with: ./dev-up.sh [--mode images|local]"

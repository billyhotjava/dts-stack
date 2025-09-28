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


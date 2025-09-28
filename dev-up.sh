#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE=".env.dts-source"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[dev-up] ${ENV_FILE} not found. Generating via init.dts-source.sh ..."
  ./init.dts-source.sh
fi

if docker compose version >/dev/null 2>&1; then
  compose_cmd=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose_cmd=(docker-compose)
else
  echo "[dev-up] ERROR: docker compose not found" >&2
  exit 1
fi

services=(
  dts-pg
  dts-admin
  dts-platform
  dts-admin-webapp
  dts-platform-webapp
  dts-public-api
)

echo "[dev-up] Starting dts-source dev services with build ..."
"${compose_cmd[@]}" --env-file "$ENV_FILE" \
  -f docker-compose.yml -f docker-compose.dts-source.yml \
  up -d --build "${services[@]}"

echo "[dev-up] Done. Stop dev services with: ./dev-stop.sh"


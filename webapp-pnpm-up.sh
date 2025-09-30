#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if docker compose version >/dev/null 2>&1; then
  compose_cmd=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose_cmd=(docker-compose)
else
  echo "[webapp-pnpm-up] ERROR: docker compose not found" >&2
  exit 1
fi

BASE="-f docker-compose.yml -f docker-compose.webapp-pnpm.yml"
MODE="online"
if [[ -d ./offline/pnpm-store ]]; then
  BASE+=" -f docker-compose.webapp-pnpm-offline.yml"
  MODE="offline"
fi

echo "[webapp-pnpm-up] Starting PNPM build+serve (${MODE}) ..."
eval "${compose_cmd[*]} $BASE up -d dts-admin-webapp-build dts-platform-webapp-build dts-admin-webapp dts-platform-webapp"

echo "[webapp-pnpm-up] Done. Static content in ./builds/* is served via nginx (18011/18012)."

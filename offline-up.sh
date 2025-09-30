#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_BASE=".env"
ENV_DEV=".env.dts-source"

if [[ ! -f "$ENV_BASE" ]]; then
  echo "[offline-up] ERROR: ${ENV_BASE} not found. Run './init.sh' first." >&2
  exit 1
fi

if [[ ! -f "$ENV_DEV" ]]; then
  echo "[offline-up] ${ENV_DEV} not found. Generating via init.dts-source.sh ..."
  ./init.dts-source.sh
fi

MAVEN_REPO_DIR="./offline/maven-repo"
if [[ ! -d "$MAVEN_REPO_DIR" ]]; then
  echo "[offline-up] ERROR: $MAVEN_REPO_DIR not found. Prepare offline assets on an online machine:"
  echo "  ./offline-prepare.sh"
  exit 2
fi

if docker compose version >/dev/null 2>&1; then
  compose_cmd=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose_cmd=(docker-compose)
else
  echo "[offline-up] ERROR: docker compose not found" >&2
  exit 1
fi

set -a
source "$ENV_BASE"
source "$ENV_DEV" || true
set +a

compose_files=(-f docker-compose.yml -f docker-compose.local-dev.yml -f docker-compose.local-offline.yml)

echo "[offline-up] Starting services in OFFLINE mode ..."
echo "[offline-up] Ensuring Traefik (dts-proxy) and Keycloak are running ..."
if docker compose -f docker-compose.yml ps -q dts-proxy >/dev/null 2>&1; then :; else "${compose_cmd[@]}" -f docker-compose.yml up -d dts-proxy; fi
if docker compose -f docker-compose.yml ps -q dts-keycloak >/dev/null 2>&1; then :; else "${compose_cmd[@]}" -f docker-compose.yml up -d dts-keycloak; fi

services=(
  dts-admin
  dts-platform
  dts-admin-webapp
  dts-platform-webapp
  dts-public-api
)
"${compose_cmd[@]}" "${compose_files[@]}" up -d "${services[@]}"

echo "[offline-up] Done. Stop with: ./local-stop"

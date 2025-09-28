#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_DEV=".env.dts-source"
ENV_BASE=".env"

if [[ ! -f "$ENV_DEV" ]]; then
  echo "[dev-up] ${ENV_DEV} not found. Generating via init.dts-source.sh ..."
  ./init.dts-source.sh
fi

if [[ ! -f "$ENV_BASE" ]]; then
  echo "[dev-up] ERROR: ${ENV_BASE} not found. Please run './init.sh' first to generate the core stack env." >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  compose_cmd=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose_cmd=(docker-compose)
else
  echo "[dev-up] ERROR: docker compose not found" >&2
  exit 1
fi

# Load both env files into current shell so compose gets complete variables
set -a
source "$ENV_BASE"
source "$ENV_DEV"
set +a

# Fill missing optional PG triplets to avoid compose interpolation warnings
set -a
: "${PG_DB_DTADMIN:=dts_admin}"
: "${PG_USER_DTADMIN:=dts_admin}"
: "${PG_PWD_DTADMIN:=dts_admin}"
set +a

core_compose=(-f docker-compose.yml -f docker-compose.dts-source.yml)

# Bring up Postgres first and ensure dev triplets exist, then start dev services
echo "[dev-up] Bringing up Postgres (for dev triplets) ..."
"${compose_cmd[@]}" "${core_compose[@]}" up -d dts-pg

echo "[dev-up] Waiting for Postgres to be ready ..."
for i in {1..60}; do
  if "${compose_cmd[@]}" exec -T dts-pg bash -lc "pg_isready -h 127.0.0.1 -p \"${PG_PORT:-5432}\" -U \"${PG_SUPER_USER:-postgres}\" -d postgres" >/dev/null 2>&1; then
    break
  fi
  echo "[dev-up]   ... (${i}/60)" >&2
  sleep 2
done

echo "[dev-up] Ensuring dev roles/databases (idempotent) ..."
"${compose_cmd[@]}" exec -T dts-pg bash -lc "/docker-entrypoint-initdb.d/99-ensure-users-runtime.sh" || true

services=(
  dts-admin
  dts-platform
  dts-admin-webapp
  dts-platform-webapp
  dts-public-api
)

echo "[dev-up] Starting dts-source dev services with build ..."
"${compose_cmd[@]}" "${core_compose[@]}" up -d --build "${services[@]}"

echo "[dev-up] Done. Stop dev services with: ./dev-stop.sh"

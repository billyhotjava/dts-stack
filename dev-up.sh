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

# Ensure Postgres from core stack is running and healthy
echo "[dev-up] Ensuring Postgres (dts-pg) is running ..."
pg_cid=$("${compose_cmd[@]}" -f docker-compose.yml ps -q dts-pg || true)
if [[ -z "${pg_cid}" ]]; then
  "${compose_cmd[@]}" -f docker-compose.yml up -d dts-pg
  pg_cid=$("${compose_cmd[@]}" -f docker-compose.yml ps -q dts-pg || true)
fi

echo "[dev-up] Waiting for dts-pg to become healthy ..."
if [[ -n "${pg_cid}" ]]; then
  for i in {1..5}; do
    status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${pg_cid}" 2>/dev/null || echo none)
    if [[ "${status}" == "healthy" ]]; then
      echo "[dev-up] dts-pg is healthy."
      break
    fi
    if [[ "${status}" == "none" ]]; then
      # No healthcheck configured (unlikely). Short grace period then continue.
      sleep 3
      break
    fi
    sleep 2
    [[ $i -eq 5 ]] && echo "[dev-up] WARNING: dts-pg not healthy yet, continuing..." >&2
  done
fi

services=(dts-admin dts-platform dts-public-api)

# Optionally include webapp containers unless disabled
if [[ "${WITH_WEBAPP:-1}" != "0" && "${SKIP_WEBAPP:-0}" != "1" ]]; then
  services+=(dts-admin-webapp dts-platform-webapp)
else
  echo "[dev-up] Webapp containers skipped (start frontend via pnpm locally)."
fi

echo "[dev-up] Starting dts-source dev services with build ..."
"${compose_cmd[@]}" "${core_compose[@]}" up -d --build "${services[@]}"

echo "[dev-up] Done. Stop dev services with: ./dev-stop.sh"

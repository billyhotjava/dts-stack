#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_DEV=".env.dts-source"
ENV_BASE=".env"

MODE="images"  # images | local
WITH_WEBAPP_DEFAULT=1

usage(){
  echo "Usage: $0 [--mode images|local] [--no-webapp]"
}

while (($#)); do
  case "$1" in
    --mode)
      shift; MODE="${1:-images}";;
    --no-webapp)
      WITH_WEBAPP_DEFAULT=0;;
    -h|--help)
      usage; exit 0;;
    *)
      echo "[dev-up] Unknown arg: $1" >&2; usage; exit 1;;
  esac
  shift
done

# Default behavior: skip webapp build in images mode (use local mode or --no-webapp)
if [[ "$MODE" == "images" && -z "${WITH_WEBAPP+x}" ]]; then
  WITH_WEBAPP_DEFAULT=0
fi

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

if [[ "$MODE" == "local" ]]; then
  compose_files=(-f docker-compose.yml -f docker-compose.dev.yml)
else
  compose_files=(-f docker-compose.yml -f docker-compose-app.yml)
fi

# Fill missing MINIO-derived vars for compose interpolation (avoid warnings)
: "${S3_REGION:=cn-local-1}"
: "${BASE_DOMAIN:=dts.local}"
if [[ -z "${HOST_MINIO:-}" ]]; then HOST_MINIO="minio.${BASE_DOMAIN}"; fi
if [[ -z "${MINIO_REGION_NAME:-}" ]]; then MINIO_REGION_NAME="${S3_REGION}"; fi
if [[ -z "${MINIO_SERVER_URL:-}" ]]; then MINIO_SERVER_URL="https://${HOST_MINIO}"; fi
if [[ -z "${MINIO_BROWSER_REDIRECT_URL:-}" ]]; then MINIO_BROWSER_REDIRECT_URL="https://${HOST_MINIO}"; fi
export MINIO_REGION_NAME MINIO_SERVER_URL MINIO_BROWSER_REDIRECT_URL

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

if [[ "$MODE" == "local" ]]; then
  echo "[dev-up] Ensuring Traefik (dts-proxy) and Keycloak are running ..."
  proxy_cid=$("${compose_cmd[@]}" -f docker-compose.yml ps -q dts-proxy || true)
  kc_cid=$("${compose_cmd[@]}" -f docker-compose.yml ps -q dts-keycloak || true)
  if [[ -z "${proxy_cid}" || -z "${kc_cid}" ]]; then
    "${compose_cmd[@]}" -f docker-compose.yml up -d dts-proxy dts-keycloak
  fi
fi

services=(dts-admin dts-platform)

WITH_WEBAPP="${WITH_WEBAPP:-$WITH_WEBAPP_DEFAULT}"
if [[ "$WITH_WEBAPP" != "0" && "${SKIP_WEBAPP:-0}" != "1" ]]; then
  services+=(dts-admin-webapp dts-platform-webapp)
else
  echo "[dev-up] Webapp containers skipped (start frontend via pnpm locally)."
fi

if [[ "$MODE" == "local" ]]; then
  echo "[dev-up] Starting local-dev services (bind mounts + live reload) ..."
  "${compose_cmd[@]}" "${compose_files[@]}" up -d "${services[@]}"
  if [[ "$WITH_WEBAPP" != "0" && "${SKIP_WEBAPP:-0}" != "1" ]]; then
    echo "[dev-up] Patching Vite env handling (best-effort) ..."
    "${compose_cmd[@]}" "${compose_files[@]}" exec -T dts-admin-webapp sh -lc "sh /patches/patch-vite-env.sh || true" || true
    "${compose_cmd[@]}" "${compose_files[@]}" exec -T dts-platform-webapp sh -lc "sh /patches/patch-vite-env.sh || true" || true
  fi
else
  echo "[dev-up] Starting dts-source dev services with build ..."
  "${compose_cmd[@]}" "${compose_files[@]}" up -d --build "${services[@]}"
fi

echo "[dev-up] Done. Stop dev services with: ./dev-stop.sh [--mode images|local]"

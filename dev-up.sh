#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_BASE=".env"

MODE="images"  # images | local
WITH_WEBAPP_DEFAULT=1
WITH_ANALYTICS_DEV=0
WITH_ANALYTICS=0

usage(){
  echo "Usage: $0 [--mode images|local] [--no-webapp] [--analytics] [--analytics-dev]"
}

checksum_file() {
  local p="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$p" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$p" | awk '{print $1}'
  else
    cksum "$p" | awk '{print $1}'
  fi
}

# Load image versions from imgversion.conf into env (non-destructive)
load_img_versions_dev(){
  local conf="imgversion.conf"
  [[ -f "$conf" ]] || return 0
  while IFS='=' read -r k v; do
    # Skip blanks/comments
    [[ -z "${k// }" || "${k#\#}" != "$k" ]] && continue
    v="$(echo "$v" | sed -E 's/^\s+|\s+$//g')"
    # Only export if not already set in environment
    eval "__cur=\${$k-}"
    if [[ -z "${__cur}" ]]; then
      export "$k=$v"
    fi
  done < <(grep -E '^[[:space:]]*([A-Z0-9_]+)[[:space:]]*=' "$conf" || true)
}

# Detect optional services via imgversion.conf toggles
determine_enabled_services(){
  local conf="imgversion.conf"
  ENABLE_MINIO="false"
  ENABLE_NESSIE="false"
  if [[ -f "$conf" ]]; then
    if rg -n "^[[:space:]]*IMAGE_MINIO[[:space:]]*=" "$conf" >/dev/null 2>&1 || grep -Eq '^[[:space:]]*IMAGE_MINIO[[:space:]]*=' "$conf"; then
      ENABLE_MINIO="true"
    fi
    if rg -n "^[[:space:]]*IMAGE_NESSIE[[:space:]]*=" "$conf" >/dev/null 2>&1 || grep -Eq '^[[:space:]]*IMAGE_NESSIE[[:space:]]*=' "$conf"; then
      ENABLE_NESSIE="true"
    fi
  fi
  export ENABLE_MINIO ENABLE_NESSIE
}

clean_maven_targets(){
  for module in dts-admin dts-platform dts-common; do
    local module_target="source/${module}/target"
    if [[ -d "${module_target}" ]]; then
      echo "[dev-up] Removing stale build output: ${module_target}"
      rm -rf "${module_target}"
    fi
  done
}

clean_node_modules(){
  for webapp in dts-platform-webapp dts-admin-webapp; do
    local webapp_dir="source/${webapp}"
    if [[ -d "${webapp_dir}" ]]; then
      echo "[dev-up] Cleaning Node.js artifacts in: ${webapp_dir}"
      # Remove node_modules
      if [[ -d "${webapp_dir}/node_modules" ]]; then
        rm -rf "${webapp_dir}/node_modules"
      fi
      # Remove pnpm artifacts
      if [[ -d "${webapp_dir}/.pnpm" ]]; then
        rm -rf "${webapp_dir}/.pnpm"
      fi
      # Remove pnpm store cache
      if [[ -d "${webapp_dir}/node_modules/.pnpm" ]]; then
        rm -rf "${webapp_dir}/node_modules/.pnpm"
      fi
      # Remove build outputs
      if [[ -d "${webapp_dir}/dist" ]]; then
        rm -rf "${webapp_dir}/dist"
      fi
      if [[ -d "${webapp_dir}/build" ]]; then
        rm -rf "${webapp_dir}/build"
      fi
      # Remove Vite cache
      if [[ -d "${webapp_dir}/node_modules/.vite" ]]; then
        rm -rf "${webapp_dir}/node_modules/.vite"
      fi
    fi
  done
}

while (($#)); do
  case "$1" in
    --mode)
      shift; MODE="${1:-images}";;
    --no-webapp)
      WITH_WEBAPP_DEFAULT=0;;
    --analytics-dev)
      WITH_ANALYTICS_DEV=1;;
    --analytics)
      WITH_ANALYTICS=1;;
    -h|--help)
      usage; exit 0;;
    *)
      echo "[dev-up] Unknown arg: $1" >&2; usage; exit 1;;
  esac
  shift
done

# Local dev default: start all self-owned apps (incl. analytics) unless explicitly disabled.
if [[ "$MODE" == "local" && "${WITH_ANALYTICS}" == "0" && "${WITH_ANALYTICS_DEV}" == "0" ]]; then
  WITH_ANALYTICS=1
fi

# Default behavior: skip webapp build in images mode (use local mode or --no-webapp)
if [[ "$MODE" == "images" && -z "${WITH_WEBAPP+x}" ]]; then
  WITH_WEBAPP_DEFAULT=0
fi

if [[ ! -f "$ENV_BASE" ]]; then
  echo "[dev-up] ERROR: ${ENV_BASE} not found. Please run './init.sh' first to generate the core stack env." >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  compose_base=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose_base=(docker-compose)
else
  echo "[dev-up] ERROR: docker compose not found" >&2
  exit 1
fi

# Guardrail: dev-up must never write to the repo `.env`.
# Use a runtime copy for Compose to avoid any accidental writes by child processes.
ENV_RUNTIME="$(mktemp -t dts-stack-env.runtime.XXXXXX)"
cp "$ENV_BASE" "$ENV_RUNTIME"
ENV_BASE_SHA="$(checksum_file "$ENV_BASE")"

cleanup_env_runtime() {
  rm -f "$ENV_RUNTIME" 2>/dev/null || true
  if [[ -f "$ENV_BASE" ]]; then
    local after_sha
    after_sha="$(checksum_file "$ENV_BASE" || true)"
    if [[ -n "$ENV_BASE_SHA" && -n "$after_sha" && "$after_sha" != "$ENV_BASE_SHA" ]]; then
      echo "[dev-up] WARNING: ${ENV_BASE} changed during run (dev-up does not modify it). Check other processes that may write to ${ENV_BASE}." >&2
    fi
  fi
}
trap cleanup_env_runtime EXIT INT TERM HUP

compose_cmd=("${compose_base[@]}")
if "${compose_base[@]}" --help 2>/dev/null | grep -q -- '--env-file'; then
  compose_cmd+=("--env-file" "$ENV_RUNTIME")
else
  echo "[dev-up] NOTE: compose does not support --env-file; falling back to default .env loading (still read-only)." >&2
fi

# Load env file into current shell so compose gets complete variables (read-only copy)
set -a
source "$ENV_RUNTIME"
set +a

# Load optional image versions into current env (does not modify files)
load_img_versions_dev

# Decide optional services
determine_enabled_services

# Fill missing optional PG triplets to avoid compose interpolation warnings
set -a
: "${PG_DB_DTADMIN:=dts_admin}"
: "${PG_USER_DTADMIN:=dts_admin}"
: "${PG_PWD_DTADMIN:=dts_admin}"
: "${PG_DB_ANALYTICS:=dts_analytics}"
: "${PG_USER_ANALYTICS:=dts_analytics}"
: "${PG_PWD_ANALYTICS:=dts_analytics}"
set +a

if [[ "$MODE" == "local" ]]; then
  compose_files=(-f docker-compose.yml -f docker-compose.dev.yml)
  if [[ "${WITH_ANALYTICS}" == "1" && "${WITH_ANALYTICS_DEV}" == "1" ]]; then
    echo "[dev-up] ERROR: --analytics and --analytics-dev are mutually exclusive" >&2
    exit 1
  fi
  if [[ "${WITH_ANALYTICS}" == "1" || "${WITH_ANALYTICS_DEV}" == "1" ]]; then
    compose_files+=(-f docker-compose.analytics.yml)
  fi
		  if [[ "${WITH_ANALYTICS_DEV}" == "1" ]]; then
		    if [[ -f "docker-compose.analytics-dev.yml" ]]; then
		      compose_files+=(-f docker-compose.analytics-dev.yml)
		    else
		      echo "[dev-up] WARNING: --analytics-dev requested but docker-compose.analytics-dev.yml not found" >&2
		      WITH_ANALYTICS_DEV="0"
		    fi
		  fi
else
  compose_files=(-f docker-compose.yml -f docker-compose-app.yml)
  if [[ "${WITH_ANALYTICS}" == "1" ]]; then
    compose_files+=(-f docker-compose.analytics.yml)
  fi
fi

# Ensure required builder image defaults for local dev
if [[ -z "${IMAGE_MAVEN:-}" ]]; then
  IMAGE_MAVEN="maven:3.9.9-eclipse-temurin-21"
fi
export IMAGE_MAVEN

# Fill MINIO-derived vars only when MinIO is enabled
if [[ "${ENABLE_MINIO}" == "true" ]]; then
  : "${S3_REGION:=cn-local-1}"
  : "${BASE_DOMAIN:=dts.local}"
  if [[ -z "${HOST_MINIO:-}" ]]; then HOST_MINIO="minio.${BASE_DOMAIN}"; fi
  if [[ -z "${MINIO_REGION_NAME:-}" ]]; then MINIO_REGION_NAME="${S3_REGION}"; fi
  if [[ -z "${MINIO_SERVER_URL:-}" ]]; then MINIO_SERVER_URL="https://${HOST_MINIO}"; fi
  if [[ -z "${MINIO_BROWSER_REDIRECT_URL:-}" ]]; then MINIO_BROWSER_REDIRECT_URL="https://${HOST_MINIO}"; fi
  export MINIO_REGION_NAME MINIO_SERVER_URL MINIO_BROWSER_REDIRECT_URL
fi

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

if [[ "${WITH_ANALYTICS}" == "1" ]]; then
  services+=(dts-analytics)
fi
if [[ "${WITH_ANALYTICS_DEV}" == "1" ]]; then
  services+=(dts-analytics)
fi
if [[ "${WITH_ANALYTICS}" == "1" || "${WITH_ANALYTICS_DEV}" == "1" ]]; then
  services+=(dts-analytics-webapp)
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
  echo "[dev-up] Starting source dev services with build ..."
  clean_maven_targets
  clean_node_modules
  "${compose_cmd[@]}" "${compose_files[@]}" up -d --build "${services[@]}"
fi

echo "[dev-up] Done. Stop dev services with: ./dev-stop.sh [--mode images|local]"

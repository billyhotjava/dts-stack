#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_BASE=".env"

MODE="images"  # images | local
WITH_WEBAPP_DEFAULT=1
WITH_ANALYTICS_DEV=0
WITH_ANALYTICS=0
FORCE_AIRFLOW_BUILD=0

usage(){
  echo "Usage: $0 [--mode images|local] [--no-webapp] [--analytics] [--analytics-dev] [--force-airflow-build]"
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
    --force-airflow-build)
      FORCE_AIRFLOW_BUILD=1;;
    -h|--help)
      usage; exit 0;;
    *)
      echo "[dev-up] Unknown arg: $1" >&2; usage; exit 1;;
  esac
  shift
done

# Local dev default: start all self-owned apps (incl. analytics) unless explicitly disabled.
if [[ "$MODE" == "local" && "${WITH_ANALYTICS}" == "0" && "${WITH_ANALYTICS_DEV}" == "0" ]]; then
  WITH_ANALYTICS_DEV=1
fi

# In local mode we always run analytics from source (like dts-admin/dts-platform).
if [[ "$MODE" == "local" && "${WITH_ANALYTICS}" == "1" ]]; then
  WITH_ANALYTICS="0"
  WITH_ANALYTICS_DEV="1"
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

wait_for_service_healthy() {
  local svc="$1"
  local max_wait="${2:-60}"
  local waited=0
  local cid=""
  cid="$("${compose_cmd[@]}" "${compose_files[@]}" ps -q "${svc}" 2>/dev/null | head -n 1 || true)"
  if [[ -z "${cid}" ]]; then
    return 1
  fi
  while (( waited < max_wait )); do
    local status
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${cid}" 2>/dev/null || echo none)"
    if [[ "${status}" == "healthy" ]]; then
      return 0
    fi
    if [[ "${status}" == "none" ]]; then
      return 0
    fi
    sleep 2
    waited=$(( waited + 2 ))
  done
  return 1
}

# Load env file into current shell so compose gets complete variables (read-only copy)
set -a
source "$ENV_RUNTIME"
set +a

# Ensure source/logs does not get recreated; logs live at repo root.
if [[ -d "source/logs" ]]; then
  rm -rf "source/logs"
fi

# Ensure local bind-mount directories exist (avoid Docker creating them as root).
mkdir -p logs/dts-admin logs/dts-platform logs/dts-analytics

# Load optional image versions into current env (does not modify files)
load_img_versions_dev

# Build the OpenMetadata-enabled Airflow image when used in dev.
build_airflow_om_image() {
  local tag="${IMAGE_AIRFLOW:-}"
  if [[ -z "${tag}" ]]; then
    return
  fi
  if [[ "${tag}" != dts-airflow-om:* && "${tag}" != dts-airflow-om ]]; then
    return
  fi
  if [[ "${FORCE_AIRFLOW_BUILD}" != "1" ]] && docker image inspect "${tag}" >/dev/null 2>&1; then
    echo "[dev-up] Using existing ${tag} (dts-airflow-om)."
    return
  fi
  if [[ ! -f "${SCRIPT_DIR}/source/dts-airflow-om/Dockerfile" ]]; then
    return
  fi
  echo "[dev-up] Building ${tag} (dts-airflow-om) ..."
  IMAGE_TAG="${tag}" \
  PIP_INDEX_URL="${PIP_INDEX_URL:-}" \
  PIP_TRUSTED_HOST="${PIP_TRUSTED_HOST:-}" \
    "${SCRIPT_DIR}/builds/airflow/build-image.sh"
}

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
: "${PG_DB_AIRBYTE:=airbyte}"
: "${PG_USER_AIRBYTE:=airbyte}"
: "${PG_PWD_AIRBYTE:=airbyte}"
: "${PG_DB_TEMPORAL:=airbyte_temporal}"
: "${PG_USER_TEMPORAL:=airbyte_temporal}"
: "${PG_PWD_TEMPORAL:=airbyte_temporal}"
set +a

if [[ "$MODE" == "local" ]]; then
  compose_files=(-f docker-compose.yml -f docker-compose.dev.yml)
else
  compose_files=(-f docker-compose.yml -f docker-compose-app.yml)
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

# Ensure required roles/databases exist (idempotent). Important after a fresh PG volume; otherwise
# apps may start before their DB users are created and fail with password errors.
echo "[dev-up] Ensuring Postgres roles/databases (idempotent) ..."
exports=""
quote_sh() { printf "'%s'" "$(printf '%s' "${1:-}" | sed "s/'/'\\\"'\\\"'/g")"; }
while IFS='=' read -r k v; do
  case "${k}" in
    PG_DB_*|PG_USER_*|PG_PWD_*)
      exports+="export ${k}=$(quote_sh "${v}");"
      ;;
  esac
done < <(env)

if [[ -n "${pg_cid}" ]]; then
  if docker exec -i "${pg_cid}" bash -lc "${exports} bash /docker-entrypoint-initdb.d/99-ensure-users-runtime.sh" >/dev/null 2>&1; then
    echo "[dev-up] Postgres roles/databases ensured."
  else
    "${compose_cmd[@]}" -f docker-compose.yml exec -T dts-pg bash -lc "${exports} bash /docker-entrypoint-initdb.d/99-ensure-users-runtime.sh" >/dev/null || \
      echo "[dev-up] WARNING: Failed to run ensure script for Postgres (continuing)." >&2
  fi
else
  echo "[dev-up] WARNING: cannot locate dts-pg container id; skip ensure users/databases." >&2
fi

if [[ "$MODE" == "local" ]]; then
  echo "[dev-up] Ensuring Traefik (dts-proxy) and Keycloak are running ..."
  proxy_cid=$("${compose_cmd[@]}" -f docker-compose.yml ps -q dts-proxy || true)
  kc_cid=$("${compose_cmd[@]}" -f docker-compose.yml ps -q dts-keycloak || true)
  if [[ -z "${proxy_cid}" || -z "${kc_cid}" ]]; then
    "${compose_cmd[@]}" -f docker-compose.yml up -d dts-proxy dts-keycloak
  fi
fi

build_airflow_om_image

services=(dts-admin dts-platform)

# Metadata/ELT stack for dev mode
services+=(dts-elasticsearch dts-openmetadata dts-airflow-init dts-airflow-webserver dts-airflow-scheduler dts-airflow-triggerer dts-dbt)
services+=(dts-airbyte-temporal dts-airbyte-bootloader dts-airbyte-server dts-airbyte-worker dts-airbyte-webapp)

WITH_WEBAPP="${WITH_WEBAPP:-$WITH_WEBAPP_DEFAULT}"
if [[ "$WITH_WEBAPP" != "0" && "${SKIP_WEBAPP:-0}" != "1" ]]; then
  services+=(dts-admin-webapp dts-platform-webapp)
else
  echo "[dev-up] Webapp containers skipped (start frontend via pnpm locally)."
fi

if [[ "${WITH_ANALYTICS}" == "1" || "${WITH_ANALYTICS_DEV}" == "1" ]]; then
  services+=(dts-analytics)
  if [[ "$WITH_WEBAPP" != "0" && "${SKIP_WEBAPP:-0}" != "1" ]]; then
    services+=(dts-analytics-webapp-modern)
  fi
fi

# Ensure Airflow is up before OpenMetadata.
airflow_services=(dts-airflow-init dts-airflow-webserver dts-airflow-scheduler dts-airflow-triggerer)
openmetadata_services=(dts-openmetadata)

# Only rebuild our dev services; keep shared infra intact.
dev_services=(dts-admin dts-platform)
if [[ "${WITH_ANALYTICS}" == "1" || "${WITH_ANALYTICS_DEV}" == "1" ]]; then
  dev_services+=(dts-analytics)
fi
other_services=()
for svc in "${services[@]}"; do
  skip=0
  for dev_svc in "${dev_services[@]}"; do
    if [[ "${svc}" == "${dev_svc}" ]]; then
      skip=1
      break
    fi
  done
  for af_svc in "${airflow_services[@]}"; do
    if [[ "${svc}" == "${af_svc}" ]]; then
      skip=1
      break
    fi
  done
  for om_svc in "${openmetadata_services[@]}"; do
    if [[ "${svc}" == "${om_svc}" ]]; then
      skip=1
      break
    fi
  done
  if [[ "${skip}" -eq 0 ]]; then
    other_services+=("${svc}")
  fi
done

if [[ "$MODE" == "local" ]]; then
  echo "[dev-up] Starting local-dev services (bind mounts + live reload) ..."
  # Keep shared services stable; only force-recreate our dev containers.
  if [[ "${#other_services[@]}" -gt 0 ]]; then
    "${compose_cmd[@]}" "${compose_files[@]}" up -d "${other_services[@]}"
  fi
  "${compose_cmd[@]}" "${compose_files[@]}" up -d "${airflow_services[@]}" >/dev/null 2>&1 || true
  if ! wait_for_service_healthy dts-airflow-webserver 90; then
    echo "[dev-up] WARNING: dts-airflow-webserver not healthy yet; continuing." >&2
  fi
  "${compose_cmd[@]}" "${compose_files[@]}" up -d "${openmetadata_services[@]}" >/dev/null 2>&1 || true
  if [[ "${#dev_services[@]}" -gt 0 ]]; then
    "${compose_cmd[@]}" "${compose_files[@]}" up -d --force-recreate "${dev_services[@]}"
  fi
  if [[ "$WITH_WEBAPP" != "0" && "${SKIP_WEBAPP:-0}" != "1" ]]; then
    echo "[dev-up] Patching Vite env handling (best-effort) ..."
    "${compose_cmd[@]}" "${compose_files[@]}" exec -T dts-admin-webapp sh -lc "sh /patches/patch-vite-env.sh || true" || true
    "${compose_cmd[@]}" "${compose_files[@]}" exec -T dts-platform-webapp sh -lc "sh /patches/patch-vite-env.sh || true" || true
  fi
else
  echo "[dev-up] Starting source dev services with build ..."
  clean_maven_targets
  clean_node_modules
  if [[ "${#other_services[@]}" -gt 0 ]]; then
    "${compose_cmd[@]}" "${compose_files[@]}" up -d "${other_services[@]}"
  fi
  "${compose_cmd[@]}" "${compose_files[@]}" up -d "${airflow_services[@]}" >/dev/null 2>&1 || true
  if ! wait_for_service_healthy dts-airflow-webserver 90; then
    echo "[dev-up] WARNING: dts-airflow-webserver not healthy yet; continuing." >&2
  fi
  "${compose_cmd[@]}" "${compose_files[@]}" up -d "${openmetadata_services[@]}" >/dev/null 2>&1 || true
  if [[ "${#dev_services[@]}" -gt 0 ]]; then
    "${compose_cmd[@]}" "${compose_files[@]}" up -d --build "${dev_services[@]}"
  fi
fi

echo "[dev-up] Done. Stop dev services with: ./dev-stop.sh [--mode images|local]"

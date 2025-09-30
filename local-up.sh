#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_BASE=".env"
ENV_DEV=".env.dts-source"

if [[ ! -f "$ENV_BASE" ]]; then
  echo "[local-up] ERROR: ${ENV_BASE} not found. Run './init.sh' first to generate the stack env." >&2
  exit 1
fi

# Optional dev env (DB triplets, toggles). Generate if missing to avoid interpolation warnings.
if [[ ! -f "$ENV_DEV" ]]; then
  echo "[local-up] ${ENV_DEV} not found. Generating via init.dts-source.sh ..."
  ./init.dts-source.sh
fi

if docker compose version >/dev/null 2>&1; then
  compose_cmd=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose_cmd=(docker-compose)
else
  echo "[local-up] ERROR: docker compose not found" >&2
  exit 1
fi

# Export env vars for compose interpolation from both files
set -a
source "$ENV_BASE"
source "$ENV_DEV" || true
set +a

# Fill missing optional PG triplets to avoid compose interpolation warnings
set -a
: "${PG_DB_DTADMIN:=dts_admin}"
: "${PG_USER_DTADMIN:=dts_admin}"
: "${PG_PWD_DTADMIN:=dts_admin}"
: "${PG_DB_DTPS:=dts_platform}"
: "${PG_USER_DTPS:=dts_platform}"
: "${PG_PWD_DTPS:=dts_platform}"
set +a

compose_files=(-f docker-compose.yml -f docker-compose.local-dev.yml)

echo "[local-up] Ensuring devtools profile is present in dts-source POMs (dev only) ..."
SRC_ROOT="${SRC_ROOT:-"$(cd .. 2>/dev/null && pwd)/dts-source"}"
if [[ -d "$SRC_ROOT" ]]; then
  SRC_ROOT="$SRC_ROOT" bash ./enable-devtools.sh || true
else
  echo "[local-up] WARN: dts-source not found at $SRC_ROOT; skip devtools patch"
fi

echo "[local-up] Ensuring Postgres (dts-pg) is running ..."
pg_cid=$("${compose_cmd[@]}" -f docker-compose.yml ps -q dts-pg || true)
if [[ -z "${pg_cid}" ]]; then
  "${compose_cmd[@]}" -f docker-compose.yml up -d dts-pg
  pg_cid=$("${compose_cmd[@]}" -f docker-compose.yml ps -q dts-pg || true)
fi

echo "[local-up] Waiting for dts-pg to become healthy ..."
if [[ -n "${pg_cid}" ]]; then
  for i in {1..20}; do
    status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${pg_cid}" 2>/dev/null || echo none)
    if [[ "${status}" == "healthy" ]]; then
      echo "[local-up] dts-pg is healthy."
      break
    fi
    if [[ "${status}" == "none" ]]; then
      sleep 3
      break
    fi
    sleep 2
    [[ $i -eq 20 ]] && echo "[local-up] WARNING: dts-pg not healthy yet, continuing..." >&2
  done
fi

echo "[local-up] Ensuring Traefik (dts-proxy) and Keycloak are running ..."
proxy_cid=$("${compose_cmd[@]}" -f docker-compose.yml ps -q dts-proxy || true)
kc_cid=$("${compose_cmd[@]}" -f docker-compose.yml ps -q dts-keycloak || true)
if [[ -z "${proxy_cid}" || -z "${kc_cid}" ]]; then
  "${compose_cmd[@]}" -f docker-compose.yml up -d dts-proxy dts-keycloak
fi

services=(dts-admin dts-platform dts-public-api)

# Optionally include webapp dev servers unless disabled
if [[ "${WITH_WEBAPP:-1}" != "0" && "${SKIP_WEBAPP:-0}" != "1" ]]; then
  services+=(dts-admin-webapp dts-platform-webapp)
else
  echo "[local-up] Webapp containers skipped (start frontend via pnpm locally)."
fi

echo "[local-up] Starting local-dev services (bind mounts + live reload) ..."
"${compose_cmd[@]}" "${compose_files[@]}" up -d "${services[@]}"

# Optionally patch Vite configs inside webapp containers to merge process.env
if [[ "${WITH_WEBAPP:-1}" != "0" && "${SKIP_WEBAPP:-0}" != "1" ]]; then
  echo "[local-up] Patching Vite env handling (merge process.env) in webapp containers ..."
  # best-effort; ignore failures if file layout differs
  "${compose_cmd[@]}" "${compose_files[@]}" exec -T dts-admin-webapp sh -lc \
    "/patches/patch-vite-env.sh || true" || true
  "${compose_cmd[@]}" "${compose_files[@]}" exec -T dts-platform-webapp sh -lc \
    "/patches/patch-vite-env.sh || true" || true
fi

echo "[local-up] Done. Stop dev services with: ./local-stop"

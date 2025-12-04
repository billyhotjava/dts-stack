#!/usr/bin/env bash
set -euo pipefail

# Minimal init for dts-source modules (dev)
# - Generates .env.dts-source with DB triplets and image tags
# - Optional: pass 'up' to build & start via compose

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE=".env.dts-source"
IMG_FILE="imgversion.dts-source.conf"

echo "[init.dts-source] Generating ${ENV_FILE} ..."
{
  if [[ -f "${IMG_FILE}" ]]; then
    cat "${IMG_FILE}"
  else
    echo "IMAGE_DTS_ADMIN=dts-admin:dev"
    echo "IMAGE_DTS_ADMIN_WEBAPP=dts-admin-webapp:dev"
    echo "IMAGE_DTS_PLATFORM=dts-platform:dev"
    echo "IMAGE_DTS_PLATFORM_WEBAPP=dts-platform-webapp:dev"
  fi
  echo
  # Postgres triplets (used by dts-pg init script)
  echo "PG_DB_DTADMIN=dts_admin"
  echo "PG_USER_DTADMIN=dts_admin"
  echo "PG_PWD_DTADMIN=dts_admin"
  echo "PG_DB_DTPS=dts_platform"
  echo "PG_USER_DTPS=dts_platform"
  echo "PG_PWD_DTPS=dts_platform"
  echo
  # Common runtime toggles (can be overridden when invoking compose)
  echo "DTS_PROFILE=dev"
  echo "EUREKA_CLIENT_ENABLED=false"
  echo "SPRING_CLOUD_CONFIG_ENABLED=false"
  echo "SPRINGDOC_API_DOCS_ENABLED=true"
  echo "SPRINGDOC_SWAGGER_UI_ENABLED=true"
} > "${ENV_FILE}"

echo "[init.dts-source] Wrote ${ENV_FILE}."

if [[ "${1:-}" == "up" ]]; then
  if docker compose version >/dev/null 2>&1; then
    compose_cmd=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    compose_cmd=(docker-compose)
  else
    echo "[init.dts-source] ERROR: docker compose not found" >&2
    exit 1
  fi
  echo "[init.dts-source] Bringing up dts-source dev services (with stack dependencies)..."
  # Only start the needed services from the core stack to avoid pulling everything
  sel_services=(dts-pg dts-admin dts-platform dts-admin-webapp dts-platform-webapp)
  "${compose_cmd[@]}" --env-file "${ENV_FILE}" -f docker-compose.yml -f docker-compose-app.yml up -d "${sel_services[@]}"
fi

echo "[init.dts-source] Done. To start services with dependencies:"
echo "  docker compose --env-file ${ENV_FILE} -f docker-compose.yml -f docker-compose-app.yml up -d dts-pg dts-admin dts-platform dts-admin-webapp dts-platform-webapp"
echo "To stop only dev services (keep others running):"
echo "  docker compose -f docker-compose.yml -f docker-compose-app.yml stop dts-admin dts-platform dts-admin-webapp dts-platform-webapp"

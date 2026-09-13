#!/usr/bin/env bash
set -euo pipefail

PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-http://127.0.0.1:18082}"
DBT_PROJECT_DIR="${DBT_PROJECT_DIR:-services/dts-dbt}"
DBT_PROFILES_DIR="${DBT_PROFILES_DIR:-services/dts-dbt/profiles}"
DATA_SOURCE_ID="${DATA_SOURCE_ID:-}"
CATALOG_ASSET_ID="${CATALOG_ASSET_ID:-}"
SERVICE_TOKEN="${SERVICE_TOKEN:-}"

echo "[1/8] platform capabilities"
curl -fsS "${PLATFORM_BASE_URL}/api/capabilities"
echo

if [[ -n "${DATA_SOURCE_ID}" ]]; then
  echo "[2/8] ODS precheck for data source ${DATA_SOURCE_ID}"
  curl -fsS -X POST "${PLATFORM_BASE_URL}/api/infra/data-sources/${DATA_SOURCE_ID}/ods-precheck" \
    -H 'Content-Type: application/json' \
    -d '{"tables":["public.demo_order"],"odsSchema":"ods_demo"}'
  echo
else
  echo "[2/8] skip ODS precheck: DATA_SOURCE_ID is empty"
fi

echo "[3/8] dbt compile"
dbt compile --project-dir "${DBT_PROJECT_DIR}" --profiles-dir "${DBT_PROFILES_DIR}"

echo "[4/8] dbt test"
dbt test --project-dir "${DBT_PROJECT_DIR}" --profiles-dir "${DBT_PROFILES_DIR}"

echo "[5/8] dbt build"
dbt build --project-dir "${DBT_PROJECT_DIR}" --profiles-dir "${DBT_PROFILES_DIR}" --select tag:dbt

if [[ -n "${CATALOG_ASSET_ID}" ]]; then
  echo "[6/8] catalog asset contract"
  curl -fsS "${PLATFORM_BASE_URL}/api/catalog/assets-v2/${CATALOG_ASSET_ID}/contract"
  echo
  curl -fsS "${PLATFORM_BASE_URL}/api/catalog/assets-v2/${CATALOG_ASSET_ID}/schema-contract"
  echo
else
  echo "[6/8] skip asset contract: CATALOG_ASSET_ID is empty"
fi

echo "[7/8] governance gaps and lineage failures"
curl -fsS "${PLATFORM_BASE_URL}/api/catalog/assets-v2/governance-gaps?size=20"
echo
curl -fsS "${PLATFORM_BASE_URL}/api/catalog/assets-v2/lineage-failures?size=20"
echo

if [[ -n "${SERVICE_TOKEN}" && -n "${CATALOG_ASSET_ID}" ]]; then
  echo "[8/8] internal permission check"
  curl -fsS -X POST "${PLATFORM_BASE_URL}/api/internal/asset-permission/check" \
    -H 'Content-Type: application/json' \
    -H 'X-DTS-Service: dts-analytics' \
    -H "X-DTS-Service-Token: ${SERVICE_TOKEN}" \
    -d "{\"username\":\"ptrdemo\",\"userRoles\":[\"花卉租赁PTR\"],\"userClassification\":\"INTERNAL\",\"action\":\"READ\",\"asset\":{\"type\":\"TABLE\",\"id\":\"${CATALOG_ASSET_ID}\"}}"
  echo
else
  echo "[8/8] skip permission check: SERVICE_TOKEN or CATALOG_ASSET_ID is empty"
fi

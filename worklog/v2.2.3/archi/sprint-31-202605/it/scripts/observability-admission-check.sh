#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${SCRIPT_DIR}"
while [[ "${ROOT_DIR}" != "/" && ! -d "${ROOT_DIR}/source" ]]; do
  ROOT_DIR="$(dirname "${ROOT_DIR}")"
done
if [[ ! -d "${ROOT_DIR}/source" ]]; then
  echo "Unable to locate repository root from ${SCRIPT_DIR}" >&2
  exit 2
fi
BASE_URL="${BASE_URL:-http://127.0.0.1:18082}"
RUN_LIVE="${RUN_LIVE:-0}"

cd "${ROOT_DIR}"

echo "== Sprint-31 F7 static audit action scan =="
grep -R "INGESTION_TASK_CREATE\\|INGESTION_TASK_EXECUTE\\|INGESTION_LINEAGE_SYNC" -n source/dts-ingestion/src/main/java | head -20
grep -R "ETL_DBT_RELEASE_GATE_READ\\|ETL_DBT_RELEASE_SUBMIT_EXECUTE\\|ETL_DBT_COMPILE_EXECUTE\\|ETL_DBT_TEST_EXECUTE" -n source/dts-platform/src/main/java | head -20
grep -R "CATALOG_ASSET_CONTRACT_VIEW\\|CATALOG_ASSET_SCHEMA_CONTRACT_VIEW\\|CATALOG_GOVERNANCE_GAP_VIEW\\|CATALOG_LINEAGE_FAILURE_REPORT_VIEW" -n source/dts-platform/src/main/java | head -20
grep -R "service_auth_denied" -n source/dts-platform/src/main/java | head -20
grep -R "analytics_permission_fallback\\|screen.permission.local_fallback" -n source/dts-analytics/src/main/java | head -20

echo
echo "== Sprint-31 F7 performance boundary scan =="
grep -R "MAX_ROWS\\|max-file-size\\|CSV 行数过多\\|Excel 行数过多" -n \
  source/dts-ingestion/src/main/java \
  source/dts-ingestion/src/main/resources \
  source/dts-platform/src/main/java \
  source/dts-platform/src/main/resources/config/application.yml | head -80

if [[ "${RUN_LIVE}" != "1" ]]; then
  echo
  echo "RUN_LIVE=0; skipped live API checks."
  exit 0
fi

echo
echo "== Sprint-31 F7 live platform checks =="
curl -sS "${BASE_URL}/api/platform/sprint27/events-console?page=0&size=20" > /tmp/dts-s31-events-console.json
curl -sS "${BASE_URL}/api/platform/sprint27/audit-evidence" > /tmp/dts-s31-audit-evidence.json
curl -sS "${BASE_URL}/api/platform/sprint27/release-governance" > /tmp/dts-s31-release-governance.json

echo "Wrote:"
echo "  /tmp/dts-s31-events-console.json"
echo "  /tmp/dts-s31-audit-evidence.json"
echo "  /tmp/dts-s31-release-governance.json"

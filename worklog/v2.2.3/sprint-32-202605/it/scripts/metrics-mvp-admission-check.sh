#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${SCRIPT_DIR}"
while [[ "${ROOT_DIR}" != "/" && ! -d "${ROOT_DIR}/source" ]]; do
  ROOT_DIR="$(dirname "${ROOT_DIR}")"
done
if [[ ! -d "${ROOT_DIR}/source" || ! -d "${ROOT_DIR}/builds" ]]; then
  echo "Unable to locate repository root from ${SCRIPT_DIR}" >&2
  exit 2
fi
BASE_URL="${BASE_URL:-http://127.0.0.1:18082}"
RUN_LIVE="${RUN_LIVE:-0}"

cd "${ROOT_DIR}"

echo "== Sprint-32 static contract scan =="
grep -R "dts-metrics" -n docker-compose-app.yml builds/dts-build.sh source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilter.java | head -120
grep -R "MetricFormulaSqlGenerator\\|MetricArtifactGenerationService\\|MetricPackValidationService" -n source/dts-metrics/src/main/java source/dts-metrics/src/test/java | head -120
grep -R "semantic-dry-run\\|preview-artifacts\\|/api/metrics/packs/import" -n source/dts-metrics/src/main/java source/dts-metrics/src/main/resources/static/metrics | head -120

if [[ "${RUN_LIVE}" != "1" ]]; then
  echo
  echo "RUN_LIVE=0; skipped live API checks."
  exit 0
fi

echo
echo "== Sprint-32 live metrics checks =="
curl -sS "${BASE_URL}/api/metrics/health" > /tmp/dts-s32-metrics-health.json
curl -sS "${BASE_URL}/api/metrics/capabilities" > /tmp/dts-s32-metrics-capabilities.json
curl -sS -X POST "${BASE_URL}/api/metrics/packs/preview-artifacts" \
  -H 'Content-Type: text/yaml' \
  --data-binary @worklog/v2.2.3/sprint-32-202605/it/fixtures/inline-flower-rental-pack.yml \
  > /tmp/dts-s32-metric-pack-preview.json
curl -sS "${BASE_URL}/api/metrics/migration/semantic-dry-run" > /tmp/dts-s32-migration-dry-run.json

echo "Wrote:"
echo "  /tmp/dts-s32-metrics-health.json"
echo "  /tmp/dts-s32-metrics-capabilities.json"
echo "  /tmp/dts-s32-metric-pack-preview.json"
echo "  /tmp/dts-s32-migration-dry-run.json"

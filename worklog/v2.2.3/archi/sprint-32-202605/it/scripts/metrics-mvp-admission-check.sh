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
REQUIRE_POSITIVE_PREVIEW="${REQUIRE_POSITIVE_PREVIEW:-0}"

cd "${ROOT_DIR}"

echo "== Sprint-32 static contract scan =="
grep -R "dts-metrics" -n docker-compose-app.yml builds/dts-build.sh source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilter.java | head -120
grep -R "MetricFormulaSqlGenerator\\|MetricArtifactGenerationService\\|MetricPackValidationService" -n source/dts-metrics/src/main/java source/dts-metrics/src/test/java | head -120
grep -R "semantic-dry-run\\|preview-artifacts\\|/api/metrics/packs/import" -n source/dts-metrics/src/main/java source/dts-metrics/src/main/resources/static/metrics | head -120
grep -R "/api/internal/domains/resolve\\|/api/internal/data-standards/resolve\\|resolveDomains\\|resolveDataStandards" -n source/dts-platform/src/main/java source/dts-metrics/src/main/java source/dts-metrics/src/test/java | head -120

if [[ "${RUN_LIVE}" != "1" ]]; then
  echo
  echo "RUN_LIVE=0; skipped live API checks."
  exit 0
fi

AUTH_ARGS=()
if [[ -n "${DTS_AUTH_HEADER:-}" ]]; then
  AUTH_ARGS+=(-H "${DTS_AUTH_HEADER}")
fi
if [[ -n "${DTS_COOKIE:-}" ]]; then
  AUTH_ARGS+=(-H "Cookie: ${DTS_COOKIE}")
fi
if [[ -n "${DTS_METRICS_TEST_USER:-}" ]]; then
  AUTH_ARGS+=(-H "X-DTS-User: ${DTS_METRICS_TEST_USER}")
fi
if [[ -n "${DTS_METRICS_TEST_ROLES:-}" ]]; then
  AUTH_ARGS+=(-H "X-DTS-Roles: ${DTS_METRICS_TEST_ROLES}")
fi
if [[ -n "${DTS_METRICS_TEST_DEPT_CODE:-${DTS_METRICS_TEST_DEPT:-}}" ]]; then
  AUTH_ARGS+=(-H "X-DTS-Dept-Code: ${DTS_METRICS_TEST_DEPT_CODE:-${DTS_METRICS_TEST_DEPT:-}}")
fi
if [[ -n "${DTS_METRICS_TEST_PERSONNEL_LEVEL:-${DTS_METRICS_TEST_CLASSIFICATION:-}}" ]]; then
  AUTH_ARGS+=(-H "X-DTS-Personnel-Level: ${DTS_METRICS_TEST_PERSONNEL_LEVEL:-${DTS_METRICS_TEST_CLASSIFICATION:-}}")
fi

curl_with_auth() {
  curl -sS "${AUTH_ARGS[@]}" "$@"
}

post_yaml() {
  local fixture="$1"
  local output="$2"
  curl_with_auth -X POST "${BASE_URL}/api/metrics/packs/preview-artifacts" \
    -H 'Content-Type: text/yaml' \
    --data-binary @"${fixture}" \
    > "${output}"
}

echo
echo "== Sprint-32 live metrics checks =="
curl_with_auth "${BASE_URL}/api/metrics/health" > /tmp/dts-s32-metrics-health.json
curl_with_auth "${BASE_URL}/api/metrics/capabilities" > /tmp/dts-s32-metrics-capabilities.json
post_yaml \
  worklog/v2.2.3/sprint-32-202605/it/fixtures/inline-flower-rental-pack.yml \
  /tmp/dts-s32-metric-pack-preview.json
if [[ "${REQUIRE_POSITIVE_PREVIEW}" == "1" ]]; then
  grep -q '"valid":true' /tmp/dts-s32-metric-pack-preview.json
fi
post_yaml \
  worklog/v2.2.3/sprint-32-202605/it/fixtures/broken-unknown-domain-pack.yml \
  /tmp/dts-s32-broken-unknown-domain-preview.json
grep -q '"valid":false' /tmp/dts-s32-broken-unknown-domain-preview.json
grep -q 'data domains are missing in platform' /tmp/dts-s32-broken-unknown-domain-preview.json
curl_with_auth -X POST "${BASE_URL}/api/metrics/packs/validate" \
  -H 'Content-Type: text/yaml' \
  --data-binary @worklog/v2.2.3/sprint-32-202605/it/fixtures/broken-no-terms-pack.yml \
  > /tmp/dts-s32-broken-no-terms-validation.json
grep -q '"valid":false' /tmp/dts-s32-broken-no-terms-validation.json
curl_with_auth "${BASE_URL}/api/metrics/migration/semantic-dry-run" > /tmp/dts-s32-migration-dry-run.json

echo "Wrote:"
echo "  /tmp/dts-s32-metrics-health.json"
echo "  /tmp/dts-s32-metrics-capabilities.json"
echo "  /tmp/dts-s32-metric-pack-preview.json"
echo "  /tmp/dts-s32-broken-unknown-domain-preview.json"
echo "  /tmp/dts-s32-broken-no-terms-validation.json"
echo "  /tmp/dts-s32-migration-dry-run.json"

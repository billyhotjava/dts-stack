#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${DTS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)}"
OUT_DIR="${DTS_SMOKE_OUT:-$ROOT_DIR/worklog/v2.2.3/sprint-26-202605/it/evidence/$(date +%Y%m%d-local)/metrics-module}"
STATIC_ROUTES="$ROOT_DIR/source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx"
DYNAMIC_ROUTES="$ROOT_DIR/source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx"
GOVERNANCE_INDICATOR_CENTER="$ROOT_DIR/source/dts-platform-webapp/src/pages/governance/IndicatorCenterPage.tsx"
GOVERNANCE_INDICATORS="$ROOT_DIR/source/dts-platform-webapp/src/pages/governance/IndicatorsPage.tsx"

mkdir -p "$OUT_DIR"

assert_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! rg -q "$pattern" "$file"; then
    echo "FAIL: $message" >&2
    exit 1
  fi
}

assert_not_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if rg -q "$pattern" "$file"; then
    echo "FAIL: $message" >&2
    exit 1
  fi
}

for file in \
  "$ROOT_DIR/source/dts-platform-webapp/src/pages/metrics/MetricCenterPage.tsx" \
  "$ROOT_DIR/source/dts-platform-webapp/src/pages/metrics/MetricDictionaryPage.tsx" \
  "$ROOT_DIR/source/dts-platform-webapp/src/pages/metrics/semantic/SemanticOverviewPage.tsx" \
  "$ROOT_DIR/source/dts-platform-webapp/src/pages/metrics/semantic/SemanticSubjectsPage.tsx" \
  "$ROOT_DIR/source/dts-platform-webapp/src/pages/metrics/semantic/SemanticObjectsPage.tsx" \
  "$ROOT_DIR/source/dts-platform-webapp/src/pages/metrics/semantic/SemanticMetricDesignerPage.tsx" \
  "$ROOT_DIR/source/dts-platform-webapp/src/pages/metrics/semantic/SemanticDatasetsPage.tsx" \
  "$ROOT_DIR/source/dts-platform-webapp/src/pages/metrics/semantic/SemanticPublishPage.tsx" \
  "$ROOT_DIR/source/dts-platform-webapp/src/pages/metrics/semantic/SemanticRunsPage.tsx"; do
  test -f "$file" || { echo "FAIL: missing metrics page $file" >&2; exit 1; }
done

assert_contains "$STATIC_ROUTES" '@/pages/metrics/MetricCenterPage' "static /metrics/center owner is not pages/metrics"
assert_contains "$STATIC_ROUTES" '@/pages/metrics/MetricDictionaryPage' "static /metrics/dictionary owner is not pages/metrics"
assert_contains "$STATIC_ROUTES" '@/pages/metrics/semantic/SemanticOverviewPage' "static /metrics/semantic owner is not pages/metrics"
assert_contains "$STATIC_ROUTES" '@/pages/metrics/semantic/SemanticMetricDesignerPage' "static semantic metric designer owner is not pages/metrics"

assert_contains "$DYNAMIC_ROUTES" '"/metrics/center": "/pages/metrics/MetricCenterPage"' "dynamic /metrics/center owner is not pages/metrics"
assert_contains "$DYNAMIC_ROUTES" '"/metrics/dictionary": "/pages/metrics/MetricDictionaryPage"' "dynamic /metrics/dictionary owner is not pages/metrics"
assert_contains "$DYNAMIC_ROUTES" '"/metrics/semantic": "/pages/metrics/semantic/SemanticOverviewPage"' "dynamic /metrics/semantic owner is not pages/metrics"
assert_contains "$DYNAMIC_ROUTES" '"/metrics/semantic/metrics": "/pages/metrics/semantic/SemanticMetricDesignerPage"' "dynamic semantic metric designer owner is not pages/metrics"

assert_not_contains "$DYNAMIC_ROUTES" '"/metrics/center": "/pages/governance/IndicatorCenterPage"' "dynamic /metrics/center still points to governance"
assert_not_contains "$DYNAMIC_ROUTES" '"/metrics/dictionary": "/pages/governance/IndicatorsPage"' "dynamic /metrics/dictionary still points to governance"
assert_not_contains "$DYNAMIC_ROUTES" '"/metrics/semantic": "/pages/modeling/SemanticModelingCenterPage"' "dynamic /metrics/semantic still points to modeling"

assert_contains "$GOVERNANCE_INDICATOR_CENTER" '@/pages/metrics/MetricCenterPage' "legacy governance indicator center is not a metrics wrapper"
assert_contains "$GOVERNANCE_INDICATORS" '@/pages/metrics/MetricDictionaryPage' "legacy governance indicators page is not a metrics wrapper"
assert_not_contains "$GOVERNANCE_INDICATOR_CENTER" 'listIndicators' "legacy governance indicator center still contains metric implementation"
assert_not_contains "$GOVERNANCE_INDICATORS" 'listIndicators' "legacy governance indicators page still contains metric implementation"

cp "$STATIC_ROUTES" "$OUT_DIR/static-routes.tsx.txt"
cp "$DYNAMIC_ROUTES" "$OUT_DIR/dynamic-resolver.tsx.txt"
cp "$GOVERNANCE_INDICATOR_CENTER" "$OUT_DIR/governance-IndicatorCenterPage.tsx.txt"
cp "$GOVERNANCE_INDICATORS" "$OUT_DIR/governance-IndicatorsPage.tsx.txt"

{
  echo "Sprint-26 metrics module ownership smoke passed"
  echo "static=$STATIC_ROUTES"
  echo "dynamic=$DYNAMIC_ROUTES"
  echo "evidence=$OUT_DIR"
} | tee "$OUT_DIR/summary.txt"

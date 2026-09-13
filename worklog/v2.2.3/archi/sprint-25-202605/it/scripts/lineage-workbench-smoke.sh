#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${DTS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)}"
OUT_DIR="${DTS_SMOKE_OUT:-$ROOT_DIR/worklog/v2.2.3/sprint-25-202605/it/evidence/$(date +%Y%m%d-local)/lineage-workbench}"
ROUTES="$ROOT_DIR/source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx"
DYNAMIC="$ROOT_DIR/source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx"
PAGE="$ROOT_DIR/source/dts-platform-webapp/src/pages/catalog/LineagePage.tsx"
DATASET_DETAIL="$ROOT_DIR/source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.tsx"
TRANSFORM_PAGE="$ROOT_DIR/source/dts-platform-webapp/src/pages/explore/etl/TransformPage.tsx"

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

for route in \
  "catalog/lineage/impact" \
  "catalog/lineage/graph" \
  "catalog/lineage/columns" \
  "catalog/lineage/import" \
  "catalog/lineage/diff"; do
  assert_contains "$ROUTES" "$route" "static lineage route $route is missing"
done

for page in LineageImpactPage LineageGraphPage LineageColumnsPage LineageImportPage LineageDiffPage; do
  assert_contains "$DYNAMIC" "$page" "dynamic resolver wrapper $page is missing"
done

assert_contains "$PAGE" "LineageSection = \"impact\" \\| \"graph\" \\| \"columns\" \\| \"import\" \\| \"diff\"" "lineage section union is missing"
assert_contains "$PAGE" "影响分析" "impact section title is missing"
assert_contains "$PAGE" "血缘图谱" "graph section title is missing"
assert_contains "$PAGE" "字段血缘" "column lineage section title is missing"
assert_contains "$PAGE" "血缘导入" "import section title is missing"
assert_contains "$PAGE" "快照对比" "diff section title is missing"
assert_contains "$PAGE" "VisualFlowCanvas" "lineage graph does not reuse VisualFlowCanvas"
assert_contains "$DATASET_DETAIL" "/catalog/lineage/graph" "asset detail does not link to global lineage graph"
assert_contains "$TRANSFORM_PAGE" "/catalog/lineage/impact" "data development task does not link to impact analysis"
assert_not_contains "$TRANSFORM_PAGE" "router.push\\(\"/catalog/lineage\"\\)" "data development task still links to lineage root"

cp "$PAGE" "$OUT_DIR/LineagePage.tsx.txt"

{
  echo "Sprint-25 lineage workbench smoke passed"
  echo "routes=$ROUTES"
  echo "dynamic=$DYNAMIC"
  echo "page=$PAGE"
  echo "evidence=$OUT_DIR"
} | tee "$OUT_DIR/summary.txt"

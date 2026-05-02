#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${DTS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)}"
OUT_DIR="${DTS_SMOKE_OUT:-$ROOT_DIR/worklog/v2.2.3/sprint-25-202605/it/evidence/$(date +%Y%m%d-local)/semantic-metrics}"
ROUTES="$ROOT_DIR/source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx"
DYNAMIC="$ROOT_DIR/source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx"
PAGE="$ROOT_DIR/source/dts-platform-webapp/src/pages/modeling/SemanticModelingCenterPage.tsx"

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
  "metrics/semantic" \
  "metrics/semantic/subjects" \
  "metrics/semantic/objects" \
  "metrics/semantic/metrics" \
  "metrics/semantic/models" \
  "metrics/semantic/publish" \
  "metrics/semantic/runs" \
  "modeling/semantic-center" \
  "bi/semantic-modeling"; do
  assert_contains "$ROUTES" "$route" "static route $route is missing"
done

for wrapper in SemanticSubjectsPage SemanticObjectsPage SemanticMetricsPage SemanticModelsPage SemanticPublishPage SemanticRunsPage; do
  assert_contains "$ROUTES" "$wrapper" "static route wrapper $wrapper is missing"
done

for dynamic_route in \
  "/metrics/semantic/subjects" \
  "/metrics/semantic/objects" \
  "/metrics/semantic/metrics" \
  "/metrics/semantic/models" \
  "/metrics/semantic/publish" \
  "/metrics/semantic/runs"; do
  assert_contains "$DYNAMIC" "$dynamic_route" "dynamic resolver route $dynamic_route is missing"
done

assert_contains "$PAGE" "DWD 明细模型开始做业务对象" "DWD input口径提示缺失"
assert_contains "$PAGE" "DWS 公共汇总模型或 ADS 应用数据集" "DWS/ADS output口径提示缺失"
assert_contains "$PAGE" "governanceDomainId" "治理主题域可选引用缺失"
assert_contains "$PAGE" "VisualFlowCanvas" "语义拖拽画布未复用 VisualFlowCanvas"
assert_contains "$PAGE" "showModels \\? <Button type=\"primary\"" "DWS/ADS 页面头部定义模型动作缺失"
assert_contains "$PAGE" "showPublish \\? <Row" "审核发布页缺失"
assert_not_contains "$PAGE" "ApiOutlined" "DWS/ADS 页面仍承载 BI 注册动作"

cp "$ROUTES" "$OUT_DIR/static-routes.tsx.txt"
cp "$DYNAMIC" "$OUT_DIR/dynamic-resolver.tsx.txt"

{
  echo "Sprint-25 semantic metrics smoke passed"
  echo "routes=$ROUTES"
  echo "dynamic=$DYNAMIC"
  echo "page=$PAGE"
  echo "evidence=$OUT_DIR"
} | tee "$OUT_DIR/summary.txt"

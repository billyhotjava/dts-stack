#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
OUT_DIR="${DTS_SMOKE_OUT:-$ROOT_DIR/worklog/v2.2.3/sprint-26-202605/it/evidence/$(date +%Y%m%d)-local/semantic-contract}"
mkdir -p "$OUT_DIR"

cd "$ROOT_DIR"

fail() {
	echo "[FAIL] $*" | tee -a "$OUT_DIR/result.log"
	exit 1
}

pass() {
	echo "[PASS] $*" | tee -a "$OUT_DIR/result.log"
}

SHARED="source/dts-platform-webapp/src/pages/metrics/semantic/semanticModelingShared.ts"
PUBLISH="source/dts-platform-webapp/src/pages/metrics/semantic/SemanticPublishPage.tsx"

grep -q "isDwdSemanticInput" "$SHARED" || fail "missing DWD semantic input contract"
grep -q "isConsumableSemanticModel" "$SHARED" || fail "missing consumable semantic model contract"
pass "shared semantic contracts exist"

for page in SemanticSubjectsPage SemanticObjectsPage SemanticMetricDesignerPage; do
	grep -q "isDwdSemanticInput" "source/dts-platform-webapp/src/pages/metrics/semantic/${page}.tsx" || fail "$page does not use DWD input contract"
done
pass "DWD input selectors use shared contract"

grep -q "models.filter(isConsumableSemanticModel)" "$PUBLISH" || fail "publish page does not filter consumable models"
grep -q "disabled={!canPublishModel}" "$PUBLISH" || fail "publish actions are not constrained by publish gate"
grep -q "请选择 DWS 公共汇总模型或 ADS 应用数据集" "$PUBLISH" || fail "publish page lacks DWS/ADS guard message"
pass "DWS/ADS publish actions are constrained"

grep -q '"/modeling/semantic-center"' source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx || fail "old semantic modeling dynamic route removed"
grep -q 'path: "modeling/semantic-center"' source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx || fail "old semantic modeling static route removed"
grep -q '"/bi/semantic-modeling"' source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx || fail "old BI semantic route removed"
grep -q '"/governance/indicator-center"' source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx || fail "old governance indicator center route removed"
pass "old semantic and governance routes remain compatible"

for script in metrics-module-smoke.sh semantic-real-page-smoke.sh lineage-real-page-smoke.sh; do
	[[ -x "worklog/v2.2.3/sprint-26-202605/it/scripts/${script}" || -f "worklog/v2.2.3/sprint-26-202605/it/scripts/${script}" ]] || fail "missing $script"
done
pass "Sprint-26 prerequisite smoke scripts exist"

echo "semantic contract smoke completed" > "$OUT_DIR/summary.txt"

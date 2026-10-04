#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
OUT_DIR="${DTS_SMOKE_OUT:-$ROOT_DIR/worklog/v2.2.3/sprint-26-202605/it/evidence/$(date +%Y%m%d)-local/lineage-real-page}"
mkdir -p "$OUT_DIR"

cd "$ROOT_DIR"

fail() {
	echo "[FAIL] $*" | tee -a "$OUT_DIR/result.log"
	exit 1
}

pass() {
	echo "[PASS] $*" | tee -a "$OUT_DIR/result.log"
}

for file in \
	source/dts-platform-webapp/src/pages/catalog/lineageShared.tsx \
	source/dts-platform-webapp/src/pages/catalog/LineageImpactPage.tsx \
	source/dts-platform-webapp/src/pages/catalog/LineageGraphPage.tsx \
	source/dts-platform-webapp/src/pages/catalog/LineageColumnsPage.tsx \
	source/dts-platform-webapp/src/pages/catalog/LineageImportPage.tsx \
	source/dts-platform-webapp/src/pages/catalog/LineageDiffPage.tsx \
	source/dts-platform-webapp/src/pages/catalog/LineagePage.tsx
do
	[[ -f "$file" ]] || fail "missing $file"
done
pass "lineage page files exist"

grep -q "getCatalogLineageImpact" source/dts-platform-webapp/src/pages/catalog/LineageImpactPage.tsx || fail "impact page does not call impact API"
grep -q "getCatalogLineageImpact" source/dts-platform-webapp/src/pages/catalog/LineageGraphPage.tsx || fail "graph page does not call impact API"
grep -q "VisualFlowCanvas" source/dts-platform-webapp/src/pages/catalog/LineageGraphPage.tsx || fail "graph page does not use shared visual canvas"
grep -q "columnLineages" source/dts-platform-webapp/src/pages/catalog/LineageColumnsPage.tsx || fail "columns page does not render column lineage"
grep -q "importDbtManifest" source/dts-platform-webapp/src/pages/catalog/LineageImportPage.tsx || fail "import page does not import dbt manifest"
grep -q "syncAddaxLineage" source/dts-platform-webapp/src/pages/catalog/LineageImportPage.tsx || fail "import page does not sync Addax lineage"
grep -q "getCatalogLineageDiff" source/dts-platform-webapp/src/pages/catalog/LineageDiffPage.tsx || fail "diff page does not call diff API"
pass "lineage pages own their API responsibilities"

if grep -q "useState" source/dts-platform-webapp/src/pages/catalog/LineagePage.tsx; then
	fail "LineagePage still owns state"
fi
grep -q "LineageImpactPage" source/dts-platform-webapp/src/pages/catalog/LineagePage.tsx || fail "LineagePage compatibility entry missing impact route"
pass "LineagePage is compatibility entry only"

for wrapper in LineageImpactPage LineageGraphPage LineageColumnsPage LineageImportPage LineageDiffPage; do
	if grep -q "<LineagePage section=" "source/dts-platform-webapp/src/pages/catalog/${wrapper}.tsx"; then
		fail "$wrapper is still a LineagePage wrapper"
	fi
done
pass "lineage sub-pages are not wrapper-only pages"

if rg -n "mock|sample|demo|示例|样例" \
	source/dts-platform-webapp/src/pages/catalog/Lineage*.tsx \
	source/dts-platform-webapp/src/pages/catalog/lineageShared.tsx \
	"$ROOT_DIR/worklog/v2.2.3/sprint-26-202605/features/F3-lineage-real-page-split/README.md" > "$OUT_DIR/forbidden-data-terms.log"; then
	fail "forbidden mock/sample/demo terms found"
fi
pass "no mock/sample/demo data terms in lineage F3 scope"

echo "lineage real page smoke completed" > "$OUT_DIR/summary.txt"

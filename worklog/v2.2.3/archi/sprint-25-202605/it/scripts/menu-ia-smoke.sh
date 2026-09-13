#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${DTS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)}"
OUT_DIR="${DTS_SMOKE_OUT:-$ROOT_DIR/worklog/v2.2.3/sprint-25-202605/it/evidence/$(date +%Y%m%d-local)/menu-ia}"
MENU_JSON="$ROOT_DIR/source/dts-admin/src/main/resources/config/data/portal-menu-seed.json"
ROLE_JSON="$ROOT_DIR/source/dts-admin/src/main/resources/config/data/role-menu-defaults.json"

mkdir -p "$OUT_DIR"

jq empty "$MENU_JSON" "$ROLE_JSON"

jq -r '.portalNavSections[] | [.key, .title, ((.children // []) | map(.title) | join("|"))] | @tsv' "$MENU_JSON" > "$OUT_DIR/portal-nav-sections.tsv"
jq -r '.[] | [.code, .title, .route, ((.requiredRoles // []) | join("|"))] | @tsv' "$ROLE_JSON" > "$OUT_DIR/role-menu-defaults.tsv"

assert_jq() {
  local expr="$1"
  local file="$2"
  local message="$3"
  if ! jq -e "$expr" "$file" > /dev/null; then
    echo "FAIL: $message" >&2
    exit 1
  fi
}

assert_absent_jq() {
  local expr="$1"
  local file="$2"
  local message="$3"
  if jq -e "$expr" "$file" > /dev/null; then
    echo "FAIL: $message" >&2
    exit 1
  fi
}

assert_jq '.portalNavSections[] | select(.key == "metrics" and .title == "语义与指标中心")' "$MENU_JSON" "metrics root menu is missing"
assert_jq '.portalNavSections[] | select(.key == "metrics") | .children[] | select(.externalLink == "/metrics/center")' "$MENU_JSON" "metrics workbench route is missing"
assert_jq '.portalNavSections[] | select(.key == "metrics") | .children[] | select(.externalLink == "/metrics/dictionary")' "$MENU_JSON" "metrics dictionary route is missing"
assert_jq '.portalNavSections[] | select(.key == "metrics") | .children[] | select(.externalLink == "/metrics/semantic/subjects")' "$MENU_JSON" "semantic subjects route is missing"
assert_jq '.portalNavSections[] | select(.key == "metrics") | .children[] | select(.externalLink == "/metrics/semantic/objects")' "$MENU_JSON" "semantic objects route is missing"
assert_jq '.portalNavSections[] | select(.key == "metrics") | .children[] | select(.externalLink == "/metrics/semantic/metrics")' "$MENU_JSON" "semantic metrics route is missing"
assert_jq '.portalNavSections[] | select(.key == "metrics") | .children[] | select(.externalLink == "/metrics/semantic/models")' "$MENU_JSON" "semantic models route is missing"
assert_jq '.portalNavSections[] | select(.key == "metrics") | .children[] | select(.externalLink == "/metrics/semantic/publish")' "$MENU_JSON" "semantic publish route is missing"
assert_jq '.portalNavSections[] | select(.key == "metrics") | .children[] | select(.externalLink == "/metrics/semantic/runs")' "$MENU_JSON" "semantic runs route is missing"

assert_absent_jq '.portalNavSections[] | select(.key == "governance") | .. | objects | select((.title? // "") | test("指标"))' "$MENU_JSON" "governance center still exposes indicator menu"
assert_absent_jq '.portalNavSections[] | select(.key == "studio") | .. | objects | select((.title? // "") | test("指标|语义"))' "$MENU_JSON" "data development center still exposes business metric menu"
assert_jq '.portalNavSections[] | select(.key == "portal") | .children[] | select(.title == "血缘与影响分析")' "$MENU_JSON" "lineage menu title is not converged"

assert_jq '.[] | select(.code == "sys.nav.portal.metricsWorkbench" and .route == "/metrics/center")' "$ROLE_JSON" "metrics role default is missing"
assert_jq '.[] | select(.code == "sys.nav.portal.metricsSubjects" and .route == "/metrics/semantic/subjects")' "$ROLE_JSON" "semantic subjects role default is missing"
assert_jq '.[] | select(.code == "sys.nav.portal.dataPortalLineageImpact" and .route == "/catalog/lineage/impact")' "$ROLE_JSON" "lineage impact role default is missing"
assert_jq '.[] | select(.code == "sys.nav.portal.dataPortalLineageImport" and .route == "/catalog/lineage/import" and ((.requiredRoles // []) | index("ADMIN")))' "$ROLE_JSON" "lineage import admin role default is missing"

{
  echo "Sprint-25 menu IA smoke passed"
  echo "menu=$MENU_JSON"
  echo "roles=$ROLE_JSON"
  echo "evidence=$OUT_DIR"
} | tee "$OUT_DIR/summary.txt"

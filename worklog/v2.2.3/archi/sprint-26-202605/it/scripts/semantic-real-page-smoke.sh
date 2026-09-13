#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${DTS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)}"
OUT_DIR="${DTS_SMOKE_OUT:-$ROOT_DIR/worklog/v2.2.3/sprint-26-202605/it/evidence/$(date +%Y%m%d-local)/semantic-real-page}"
METRICS_SEMANTIC_DIR="$ROOT_DIR/source/dts-platform-webapp/src/pages/metrics/semantic"
MODELING_COMPAT="$ROOT_DIR/source/dts-platform-webapp/src/pages/modeling/SemanticModelingCenterPage.tsx"
OVERVIEW_PAGE="$METRICS_SEMANTIC_DIR/SemanticOverviewPage.tsx"
SUBJECTS_PAGE="$METRICS_SEMANTIC_DIR/SemanticSubjectsPage.tsx"
OBJECTS_PAGE="$METRICS_SEMANTIC_DIR/SemanticObjectsPage.tsx"
METRICS_PAGE="$METRICS_SEMANTIC_DIR/SemanticMetricDesignerPage.tsx"
DATASETS_PAGE="$METRICS_SEMANTIC_DIR/SemanticDatasetsPage.tsx"
PUBLISH_PAGE="$METRICS_SEMANTIC_DIR/SemanticPublishPage.tsx"
RUNS_PAGE="$METRICS_SEMANTIC_DIR/SemanticRunsPage.tsx"
WORKSPACE_PAGE="$METRICS_SEMANTIC_DIR/SemanticWorkspacePage.tsx"

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

test -f "$WORKSPACE_PAGE" || { echo "FAIL: missing SemanticWorkspacePage" >&2; exit 1; }
test -f "$OVERVIEW_PAGE" || { echo "FAIL: missing SemanticOverviewPage" >&2; exit 1; }
test -f "$SUBJECTS_PAGE" || { echo "FAIL: missing SemanticSubjectsPage" >&2; exit 1; }
test -f "$OBJECTS_PAGE" || { echo "FAIL: missing SemanticObjectsPage" >&2; exit 1; }
test -f "$METRICS_PAGE" || { echo "FAIL: missing SemanticMetricDesignerPage" >&2; exit 1; }
test -f "$DATASETS_PAGE" || { echo "FAIL: missing SemanticDatasetsPage" >&2; exit 1; }
test -f "$PUBLISH_PAGE" || { echo "FAIL: missing SemanticPublishPage" >&2; exit 1; }
test -f "$RUNS_PAGE" || { echo "FAIL: missing SemanticRunsPage" >&2; exit 1; }
test -f "$MODELING_COMPAT" || { echo "FAIL: missing modeling compatibility wrapper" >&2; exit 1; }

assert_contains "$MODELING_COMPAT" '@/pages/metrics/semantic/SemanticWorkspacePage' "old modeling page is not a metrics semantic wrapper"
assert_not_contains "$MODELING_COMPAT" 'useState' "old modeling compatibility wrapper still owns state"
assert_contains "$OVERVIEW_PAGE" 'listSemanticSubjectDomains' "semantic overview does not load real domain API"
assert_contains "$OVERVIEW_PAGE" 'listSemanticBusinessObjects' "semantic overview does not load real object API"
assert_contains "$OVERVIEW_PAGE" 'listSemanticMetrics' "semantic overview does not load real metric API"
assert_not_contains "$OVERVIEW_PAGE" 'SemanticWorkspacePage' "semantic overview still delegates to workspace"
assert_contains "$SUBJECTS_PAGE" 'listSemanticSubjectDomains' "semantic subjects page does not load real semantic domain API"
assert_contains "$SUBJECTS_PAGE" 'createSemanticSubjectDomain' "semantic subjects page cannot create subject domains"
assert_contains "$SUBJECTS_PAGE" 'getDomainTree' "semantic subjects page does not reference governance domain tree"
assert_contains "$SUBJECTS_PAGE" 'listDatasets' "semantic subjects page does not show DWD candidates"
assert_not_contains "$SUBJECTS_PAGE" 'SemanticWorkspacePage' "semantic subjects page still delegates to workspace"
assert_contains "$OBJECTS_PAGE" 'listSemanticBusinessObjects' "semantic objects page does not load real object API"
assert_contains "$OBJECTS_PAGE" 'createSemanticBusinessObject' "semantic objects page cannot create business objects"
assert_contains "$OBJECTS_PAGE" 'listSemanticObjectTableMappings' "semantic objects page does not load table mappings"
assert_contains "$OBJECTS_PAGE" 'saveSemanticObjectTableMappings' "semantic objects page cannot save join mappings"
assert_contains "$OBJECTS_PAGE" 'VisualFlowCanvas' "semantic objects page does not use visual join canvas"
assert_not_contains "$OBJECTS_PAGE" 'SemanticWorkspacePage' "semantic objects page still delegates to workspace"
assert_contains "$METRICS_PAGE" 'listSemanticDimensions' "semantic metrics page does not load dimensions"
assert_contains "$METRICS_PAGE" 'listSemanticMetrics' "semantic metrics page does not load metrics"
assert_contains "$METRICS_PAGE" 'createSemanticDimension' "semantic metrics page cannot save dimensions"
assert_contains "$METRICS_PAGE" 'createSemanticMetric' "semantic metrics page cannot save metrics"
assert_contains "$METRICS_PAGE" 'getDatasetFields' "semantic metrics page does not load DWD fields"
assert_contains "$METRICS_PAGE" 'VisualFlowCanvas' "semantic metrics page does not use visual drag canvas"
assert_not_contains "$METRICS_PAGE" 'SemanticWorkspacePage' "semantic metrics page still delegates to workspace"
assert_contains "$DATASETS_PAGE" 'createSemanticModel' "semantic datasets page cannot create models"
assert_contains "$DATASETS_PAGE" 'saveSemanticModelBindings' "semantic datasets page cannot save model bindings"
assert_contains "$DATASETS_PAGE" 'generateSemanticModelArtifacts' "semantic datasets page cannot generate artifacts"
assert_contains "$DATASETS_PAGE" 'previewSemanticModelData' "semantic datasets page cannot preview model data"
assert_contains "$DATASETS_PAGE" 'VisualFlowCanvas' "semantic datasets page does not use visual model canvas"
assert_not_contains "$DATASETS_PAGE" 'SemanticWorkspacePage' "semantic datasets page still delegates to workspace"
assert_contains "$PUBLISH_PAGE" 'submitSemanticModelReview' "semantic publish page cannot submit review"
assert_contains "$PUBLISH_PAGE" 'approveSemanticModelReview' "semantic publish page cannot approve review"
assert_contains "$PUBLISH_PAGE" 'publishSemanticModelToDbt' "semantic publish page cannot publish dbt"
assert_contains "$PUBLISH_PAGE" 'registerSemanticBiDataset' "semantic publish page cannot register BI dataset"
assert_contains "$PUBLISH_PAGE" 'registerSemanticLineage' "semantic publish page cannot register lineage"
assert_not_contains "$PUBLISH_PAGE" 'SemanticWorkspacePage' "semantic publish page still delegates to workspace"
assert_contains "$RUNS_PAGE" 'listSemanticModelRuns' "semantic runs page does not load run records"
assert_contains "$RUNS_PAGE" 'triggerSemanticModelRun' "semantic runs page cannot trigger runs"
assert_not_contains "$RUNS_PAGE" 'SemanticWorkspacePage' "semantic runs page still delegates to workspace"

if rg -q '@/pages/modeling/SemanticModelingCenterPage|from "./SemanticModelingCenterPage"|from "@/pages/modeling' "$METRICS_SEMANTIC_DIR"; then
  echo "FAIL: metrics semantic pages still import modeling implementation" >&2
  exit 1
fi

cp "$MODELING_COMPAT" "$OUT_DIR/modeling-SemanticModelingCenterPage.tsx.txt"
cp "$OVERVIEW_PAGE" "$OUT_DIR/metrics-SemanticOverviewPage.tsx.txt"
cp "$SUBJECTS_PAGE" "$OUT_DIR/metrics-SemanticSubjectsPage.tsx.txt"
cp "$OBJECTS_PAGE" "$OUT_DIR/metrics-SemanticObjectsPage.tsx.txt"
cp "$METRICS_PAGE" "$OUT_DIR/metrics-SemanticMetricDesignerPage.tsx.txt"
cp "$DATASETS_PAGE" "$OUT_DIR/metrics-SemanticDatasetsPage.tsx.txt"
cp "$PUBLISH_PAGE" "$OUT_DIR/metrics-SemanticPublishPage.tsx.txt"
cp "$RUNS_PAGE" "$OUT_DIR/metrics-SemanticRunsPage.tsx.txt"

{
  echo "Sprint-26 semantic real-page smoke passed"
  echo "semantic_dir=$METRICS_SEMANTIC_DIR"
  echo "compat=$MODELING_COMPAT"
  echo "evidence=$OUT_DIR"
} | tee "$OUT_DIR/summary.txt"

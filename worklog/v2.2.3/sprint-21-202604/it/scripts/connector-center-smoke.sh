#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${DTS_BASE_URL:-http://localhost:8080}"
TOKEN="${DTS_TOKEN:-}"
DATA_SOURCE_ID="${DTS_DATA_SOURCE_ID:-}"
SCHEMA_NAME="${DTS_SCHEMA:-dts_smoke}"
TABLE_PATTERN="${DTS_TABLE_PATTERN:-erp_project}"
ODS_REQUEST_FILE="${DTS_ODS_REQUEST_FILE:-worklog/v2.2.3/sprint-21-202604/it/samples/ods-request-postgres.json}"
TASK_ID="${DTS_TASK_ID:-}"
OUT_DIR="${DTS_SMOKE_OUT:-/tmp/dts-sprint21-smoke}"

mkdir -p "$OUT_DIR"

headers=(-H "Content-Type: application/json")
if [[ -n "$TOKEN" ]]; then
  headers+=(-H "Authorization: Bearer $TOKEN")
fi

curl_json() {
  local method="$1"
  local path="$2"
  local payload="${3:-}"
  local output="$4"
  if [[ -n "$payload" ]]; then
    curl -fsS -X "$method" "${headers[@]}" --data "$payload" "$BASE_URL$path" -o "$output"
  else
    curl -fsS -X "$method" "${headers[@]}" "$BASE_URL$path" -o "$output"
  fi
}

require_data_source() {
  if [[ -z "$DATA_SOURCE_ID" ]]; then
    echo "DTS_DATA_SOURCE_ID is required. Create/test a JDBC data source first, then rerun this smoke script." >&2
    exit 2
  fi
}

require_data_source

echo "[1/7] Schema Discover"
discover_payload=$(cat <<JSON
{"schema":"$SCHEMA_NAME","tablePattern":"$TABLE_PATTERN","maxTables":5,"sampleLimit":5,"includeColumns":true,"includeIndexes":true,"includeSample":true,"forceRefresh":true}
JSON
)
curl_json POST "/api/infra/data-sources/$DATA_SOURCE_ID/schema-discover" "$discover_payload" "$OUT_DIR/01-schema-discover.json"

if [[ ! -f "$ODS_REQUEST_FILE" ]]; then
  echo "ODS request file not found: $ODS_REQUEST_FILE" >&2
  exit 3
fi
ods_payload="$(<"$ODS_REQUEST_FILE")"

echo "[2/7] ODS Preview"
curl_json POST "/api/infra/data-sources/$DATA_SOURCE_ID/ods-preview" "$ods_payload" "$OUT_DIR/02-ods-preview.json"

echo "[3/7] ODS Precheck"
curl_json POST "/api/infra/data-sources/$DATA_SOURCE_ID/ods-precheck" "$ods_payload" "$OUT_DIR/03-ods-precheck.json"

echo "[4/7] ODS Apply"
curl_json POST "/api/infra/data-sources/$DATA_SOURCE_ID/ods-apply" "$ods_payload" "$OUT_DIR/04-ods-apply.json"

echo "[5/7] Sync Task Draft"
curl_json POST "/api/infra/data-sources/$DATA_SOURCE_ID/sync-task-draft" "$ods_payload" "$OUT_DIR/05-sync-task-draft.json"

if command -v jq >/dev/null 2>&1; then
  task_payload="$(jq -c '.data.payload // .payload // empty' "$OUT_DIR/05-sync-task-draft.json")"
  if [[ -n "$task_payload" ]]; then
    echo "[6/7] Create Ingestion Task"
    curl_json POST "/api/ingestion/tasks" "$task_payload" "$OUT_DIR/06-ingestion-task-create.json"
    created_task_id="$(jq -r '.data.task.id // .task.id // empty' "$OUT_DIR/06-ingestion-task-create.json")"
    if [[ -n "$created_task_id" && "$created_task_id" != "null" ]]; then
      TASK_ID="$created_task_id"
    fi
  else
    echo "[6/7] Create Ingestion Task skipped: sync-task-draft payload not found"
  fi
else
  echo "[6/7] Create Ingestion Task skipped: jq is required to extract draft payload"
fi

if [[ -n "$TASK_ID" ]]; then
  echo "[7/8] Run Center execution submit"
  curl_json POST "/api/ingestion/tasks/$TASK_ID/execute/async" "" "$OUT_DIR/07-execute-async.json"
  echo "[8/8] Backfill submit, latest execution and observability"
  backfill_payload='{"windowStart":"2026-04-29T00:00:00Z","windowEnd":"2026-04-30T00:00:00Z","column":"update_time"}'
  curl_json POST "/api/ingestion/tasks/$TASK_ID/backfill" "$backfill_payload" "$OUT_DIR/08-backfill.json"
  curl_json GET "/api/ingestion/tasks/$TASK_ID/executions/latest" "" "$OUT_DIR/09-latest-execution.json"
  curl_json GET "/api/ingestion/tasks/executions/observability?taskId=$TASK_ID&days=7" "" "$OUT_DIR/10-observability.json"
else
  echo "[7/8] Run Center execution skipped: DTS_TASK_ID not supplied and no task id was created"
fi

echo "Smoke outputs written to $OUT_DIR"

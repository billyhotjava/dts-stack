#!/usr/bin/env bash
set -euo pipefail

DTS_INGESTION_URL="${DTS_INGESTION_URL:-http://127.0.0.1:18083}"
DTS_SMOKE_OUT="${DTS_SMOKE_OUT:-/tmp/dts-sprint22-api-runtime-smoke}"
DTS_API_TASK_ID="${DTS_API_TASK_ID:-}"
DTS_API_EXECUTION_ID="${DTS_API_EXECUTION_ID:-}"
DTS_API_BACKFILL_COLUMN="${DTS_API_BACKFILL_COLUMN:-updatedAt}"
DTS_API_BACKFILL_START="${DTS_API_BACKFILL_START:-2026-01-01T00:00:00Z}"
DTS_API_BACKFILL_END="${DTS_API_BACKFILL_END:-2026-01-02T00:00:00Z}"

mkdir -p "$DTS_SMOKE_OUT"

headers=()
if [[ -n "${DTS_TOKEN:-}" ]]; then
  headers+=(-H "Authorization: Bearer ${DTS_TOKEN}")
fi
if [[ -n "${DTS_COOKIE:-}" ]]; then
  headers+=(-H "Cookie: ${DTS_COOKIE}")
fi
if [[ -n "${DTS_SERVICE_HEADER:-}" ]]; then
  headers+=(-H "X-DTS-Service: ${DTS_SERVICE_HEADER}")
fi
if [[ -n "${DTS_SERVICE_TOKEN:-}" ]]; then
  headers+=(-H "X-DTS-Service-Token: ${DTS_SERVICE_TOKEN}")
fi

request_json() {
  local method="$1"
  local path="$2"
  local prefix="$3"
  local data="${4:-}"
  local body_file="$DTS_SMOKE_OUT/${prefix}.body.json"
  local status_file="$DTS_SMOKE_OUT/${prefix}.status.txt"
  local header_file="$DTS_SMOKE_OUT/${prefix}.headers.txt"
  local args=(-sS -X "$method" -D "$header_file" -o "$body_file" -w "%{http_code}" "${headers[@]}" -H "Content-Type: application/json")
  if [[ -n "$data" ]]; then
    args+=("--data-binary" "$data")
  fi
  local status
  status="$(curl "${args[@]}" "${DTS_INGESTION_URL}${path}")"
  printf '%s\n' "$status" > "$status_file"
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "Request failed: $method $path -> $status" >&2
    cat "$body_file" >&2 || true
    return 1
  fi
}

echo "[1/5] API connector contract"
request_json GET "/api/ingestion/api/contract" "01-contract"

echo "[2/5] API auth providers"
request_json GET "/api/ingestion/api/auth-providers" "02-auth-providers"

if [[ -n "$DTS_API_TASK_ID" ]]; then
  echo "[3/5] execute API ingestion task"
  request_json POST "/api/ingestion/tasks/${DTS_API_TASK_ID}/execute/async" "03-execute"

  echo "[4/5] backfill API ingestion task"
  request_json POST "/api/ingestion/tasks/${DTS_API_TASK_ID}/backfill" "04-backfill" \
    "{\"column\":\"${DTS_API_BACKFILL_COLUMN}\",\"windowStart\":\"${DTS_API_BACKFILL_START}\",\"windowEnd\":\"${DTS_API_BACKFILL_END}\"}"

  if [[ -n "$DTS_API_EXECUTION_ID" ]]; then
    echo "[5/5] retry API ingestion execution"
    request_json POST "/api/ingestion/tasks/${DTS_API_TASK_ID}/executions/${DTS_API_EXECUTION_ID}/retry/async?mode=FAILED_ONLY" "05-retry"
  else
    echo "[5/5] retry skipped: set DTS_API_EXECUTION_ID to validate retry path"
  fi
else
  echo "[3/5] execute skipped: set DTS_API_TASK_ID to validate runtime execution"
  echo "[4/5] backfill skipped"
  echo "[5/5] retry skipped"
fi

if command -v jq >/dev/null 2>&1; then
  contract_version="$(jq -r '.contractVersion // empty' "$DTS_SMOKE_OUT/01-contract.body.json" 2>/dev/null || true)"
  connector_type="$(jq -r '.connectorType // empty' "$DTS_SMOKE_OUT/01-contract.body.json" 2>/dev/null || true)"
  jq -n \
    --arg ingestionUrl "$DTS_INGESTION_URL" \
    --arg taskId "$DTS_API_TASK_ID" \
    --arg executionId "$DTS_API_EXECUTION_ID" \
    --arg contractVersion "$contract_version" \
    --arg connectorType "$connector_type" \
    '{
      ingestionUrl: $ingestionUrl,
      taskId: ($taskId | select(length > 0)),
      executionId: ($executionId | select(length > 0)),
      contractVersion: $contractVersion,
      connectorType: $connectorType
    }' > "$DTS_SMOKE_OUT/summary.json" 2>/dev/null || true
fi

echo "Sprint-22 API runtime smoke evidence written to $DTS_SMOKE_OUT"

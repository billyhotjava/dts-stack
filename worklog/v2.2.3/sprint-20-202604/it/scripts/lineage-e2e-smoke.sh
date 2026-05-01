#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${DTS_BASE_URL:-http://127.0.0.1:18082}"
TOKEN="${DTS_TOKEN:-}"
COOKIE="${DTS_COOKIE:-}"
SERVICE="${DTS_SERVICE_HEADER:-}"
DATASET_ID="${DTS_DATASET_ID:-}"
OUT_DIR="${DTS_SMOKE_OUT:-worklog/v2.2.3/sprint-20-202604/it/evidence/local}"
SNAPSHOT_TO="${DTS_SNAPSHOT_TO:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
SNAPSHOT_FROM="${DTS_SNAPSHOT_FROM:-2020-01-01T00:00:00Z}"

mkdir -p "$OUT_DIR"

headers=()
if [[ -n "$TOKEN" ]]; then
  headers+=("-H" "Authorization: Bearer $TOKEN")
fi
if [[ -n "$COOKIE" ]]; then
  headers+=("-H" "Cookie: $COOKIE")
fi
if [[ -n "$SERVICE" ]]; then
  headers+=("-H" "X-DTS-Service: $SERVICE")
fi

request() {
  local name="$1"
  local method="$2"
  local url="$3"
  local status
  status="$(
    curl -sS \
      -w "%{http_code}" \
      -D "$OUT_DIR/$name.headers.txt" \
      -o "$OUT_DIR/$name.body.json" \
      "${headers[@]}" \
      -X "$method" \
      "$url"
  )"
  printf "%s\n" "$status" > "$OUT_DIR/$name.status.txt"
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "HTTP $status: $url" >&2
    return 1
  fi
}

if [[ -z "$DATASET_ID" ]]; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "DTS_DATASET_ID is required when docker is unavailable." >&2
    exit 2
  fi
  DATASET_ID="$(
    docker exec v223-dts-pg-1 psql -U dts_platform -d dts_platform -At \
      -c "select upstream_dataset_id from catalog_dataset_lineage where valid_to is null limit 1"
  )"
fi

if [[ -z "$DATASET_ID" ]]; then
  echo "No lineage dataset found. Run /api/catalog/lineage/sync-addax or import dbt manifest first." >&2
  exit 3
fi

echo "[1/5] current impact with jobs and columns"
request "01-impact-current" "GET" \
  "$BASE_URL/api/catalog/lineage/impact?datasetId=$DATASET_ID&direction=BOTH&depth=3&withJobs=true&withColumns=true"

echo "[2/5] snapshot impact"
request "02-impact-snapshot" "GET" \
  "$BASE_URL/api/catalog/lineage/impact?datasetId=$DATASET_ID&direction=BOTH&depth=3&withJobs=true&withColumns=true&at=$SNAPSHOT_TO"

echo "[3/5] time-travel diff"
request "03-lineage-diff" "GET" \
  "$BASE_URL/api/catalog/lineage/diff?datasetId=$DATASET_ID&direction=BOTH&depth=3&from=$SNAPSHOT_FROM&to=$SNAPSHOT_TO"

echo "[4/5] addax lineage sync"
request "04-sync-addax" "POST" \
  "$BASE_URL/api/catalog/lineage/sync-addax"

echo "[5/5] post-sync impact"
request "05-impact-after-sync" "GET" \
  "$BASE_URL/api/catalog/lineage/impact?datasetId=$DATASET_ID&direction=BOTH&depth=3&withJobs=true&withColumns=true"

if command -v jq >/dev/null 2>&1; then
  jq -n \
    --arg datasetId "$DATASET_ID" \
    --arg from "$SNAPSHOT_FROM" \
    --arg to "$SNAPSHOT_TO" \
    --slurpfile impact "$OUT_DIR/05-impact-after-sync.body.json" \
    --slurpfile diff "$OUT_DIR/03-lineage-diff.body.json" \
    '{
      datasetId: $datasetId,
      from: $from,
      to: $to,
      nodeCount: ($impact[0].data.nodeCount // null),
      edgeCount: ($impact[0].data.edgeCount // null),
      columnLineageCount: ($impact[0].data.impactStats.columnLineageCount // null),
      diffAddedCount: ($diff[0].data.addedCount // null),
      diffRemovedCount: ($diff[0].data.removedCount // null)
    }' > "$OUT_DIR/summary.json"
fi

echo "Sprint-20 lineage smoke evidence written to $OUT_DIR"

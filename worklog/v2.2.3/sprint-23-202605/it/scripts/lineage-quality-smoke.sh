#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${DTS_BASE_URL:-http://127.0.0.1:18082}"
COOKIE="${DTS_COOKIE:-}"
TOKEN="${DTS_TOKEN:-}"
OUT_DIR="${DTS_SMOKE_OUT:-worklog/v2.2.3/sprint-23-202605/it/evidence/local}"
ASSET_ID="${DTS_OM_ASSET_ID:-}"
LEGACY_DATASET_ID="${DTS_LEGACY_DATASET_ID:-}"

mkdir -p "$OUT_DIR"

headers=()
if [[ -n "$COOKIE" ]]; then
  headers+=("-H" "Cookie: $COOKIE")
fi
if [[ -n "$TOKEN" ]]; then
  headers+=("-H" "Authorization: Bearer $TOKEN")
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

if [[ -n "$ASSET_ID" ]]; then
  request "04-om-lineage-sync-response" "POST" "$BASE_URL/api/catalog/assets-v2/$ASSET_ID/lineage/sync?upstreamDepth=2&downstreamDepth=2"
  request "05-om-lineage-cache-response" "GET" "$BASE_URL/api/catalog/assets-v2/$ASSET_ID/lineage"
fi

if [[ -n "$LEGACY_DATASET_ID" ]]; then
  request "06-quality-response" "GET" "$BASE_URL/api/catalog/datasets/$LEGACY_DATASET_ID/quality"
  request "07-dts-lineage-impact-response" "GET" "$BASE_URL/api/catalog/lineage/impact?datasetId=$LEGACY_DATASET_ID&direction=BOTH&depth=3&withJobs=true&withColumns=true"
fi

echo "Sprint-23 lineage/quality smoke evidence written to $OUT_DIR"

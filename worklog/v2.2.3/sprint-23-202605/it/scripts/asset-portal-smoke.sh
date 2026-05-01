#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${DTS_BASE_URL:-http://127.0.0.1:18082}"
COOKIE="${DTS_COOKIE:-}"
TOKEN="${DTS_TOKEN:-}"
OUT_DIR="${DTS_SMOKE_OUT:-worklog/v2.2.3/sprint-23-202605/it/evidence/local}"
LIMIT="${DTS_OM_SYNC_LIMIT:-50}"

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

request "01-om-sync-response" "POST" "$BASE_URL/api/catalog/assets-v2/sync?limit=$LIMIT"
request "02-asset-list-response" "GET" "$BASE_URL/api/catalog/assets-v2?page=0&size=10"
request "03-mapping-diagnostics" "GET" "$BASE_URL/api/catalog/assets-v2/diagnostics"

echo "Sprint-23 asset portal smoke evidence written to $OUT_DIR"

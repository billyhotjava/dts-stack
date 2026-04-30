#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${DTS_BASE_URL:-http://localhost:8080}"
TOKEN="${DTS_TOKEN:-}"
COOKIE="${DTS_COOKIE:-}"
COOKIE_JAR="${DTS_COOKIE_JAR:-}"
CSV_FILE="${DTS_FILE_SOURCE_SAMPLE:-worklog/v2.2.3/sprint-21-202604/it/samples/budget-upload.csv}"
OUT_DIR="${DTS_FILE_SMOKE_OUT:-/tmp/dts-sprint21-file-smoke}"

mkdir -p "$OUT_DIR"

auth_headers=()
json_headers=(-H "Content-Type: application/json")
if [[ -n "$TOKEN" ]]; then
  auth_headers+=(-H "Authorization: Bearer $TOKEN")
  json_headers+=(-H "Authorization: Bearer $TOKEN")
fi
if [[ -n "$COOKIE" ]]; then
  auth_headers+=(-H "Cookie: $COOKIE")
  json_headers+=(-H "Cookie: $COOKIE")
fi
cookie_args=()
if [[ -n "$COOKIE_JAR" ]]; then
  cookie_args=(-b "$COOKIE_JAR")
fi

if [[ ! -f "$CSV_FILE" ]]; then
  echo "CSV sample not found: $CSV_FILE" >&2
  exit 2
fi

echo "[1/4] Upload Excel/CSV file"
curl -fsS -X POST "${auth_headers[@]}" -F "file=@$CSV_FILE" \
  "${cookie_args[@]}" \
  "$BASE_URL/api/infra/excel-import/prepare" \
  -o "$OUT_DIR/01-file-prepare.json"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to extract fileId from prepare response" >&2
  exit 3
fi

file_id="$(jq -r '.data.fileId // .fileId // empty' "$OUT_DIR/01-file-prepare.json")"
if [[ -z "$file_id" || "$file_id" == "null" ]]; then
  echo "fileId not found in $OUT_DIR/01-file-prepare.json" >&2
  exit 4
fi

echo "[2/4] Parse CSV into normalized staging file"
parse_payload=$(cat <<JSON
{"fileId":"$file_id","headerRow":1,"dataStartRow":2,"delimiter":",","previewLimit":20,"skipErrors":true,"fillMerged":false,"dateFormat":"yyyy-MM-dd HH:mm:ss"}
JSON
)
curl -fsS -X POST "${json_headers[@]}" --data "$parse_payload" \
  "${cookie_args[@]}" \
  "$BASE_URL/api/infra/excel-import/parse" \
  -o "$OUT_DIR/02-file-parse.json"

echo "[3/4] Fetch bad-row preview"
curl -fsS -X GET "${auth_headers[@]}" \
  "${cookie_args[@]}" \
  "$BASE_URL/api/infra/excel-import/errors?fileId=$file_id&limit=20" \
  -o "$OUT_DIR/03-file-errors.json"

echo "[4/4] Build file-source smoke summary"
jq -r '
  .data as $d |
  [
    "fileId=" + ($d.fileId // ""),
    "batchCode=" + ($d.batchCode // ""),
    "sheetName=" + ($d.sheetName // ""),
    "rowCount=" + (($d.rowCount // 0) | tostring),
    "errorCount=" + (($d.errorCount // 0) | tostring),
    "csvContainerPath=" + ($d.csvContainerPath // "")
  ] | .[]
' "$OUT_DIR/02-file-parse.json" > "$OUT_DIR/04-file-summary.txt"

echo "File-source smoke outputs written to $OUT_DIR"

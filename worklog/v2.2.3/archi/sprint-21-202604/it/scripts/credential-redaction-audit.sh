#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${DTS_BASE_URL:-http://localhost:8080}"
TOKEN="${DTS_TOKEN:-}"
COOKIE="${DTS_COOKIE:-}"
COOKIE_JAR="${DTS_COOKIE_JAR:-}"
DATA_SOURCE_ID="${DTS_DATA_SOURCE_ID:-}"
SECRET_SENTINEL="${DTS_SECRET_SENTINEL:-}"
OUT_DIR="${DTS_REDACTION_OUT:-/tmp/dts-sprint21-redaction}"
SCAN_DIRS="${DTS_REDACTION_SCAN_DIRS:-/tmp/dts-sprint21-smoke:/tmp/dts-sprint21-file-smoke:$OUT_DIR}"
AUDIT_EXPORT_QUERY="${DTS_AUDIT_EXPORT_QUERY:-}"

mkdir -p "$OUT_DIR"

headers=(-H "Content-Type: application/json")
auth_headers=()
if [[ -n "$TOKEN" ]]; then
  headers+=(-H "Authorization: Bearer $TOKEN")
  auth_headers+=(-H "Authorization: Bearer $TOKEN")
fi
if [[ -n "$COOKIE" ]]; then
  headers+=(-H "Cookie: $COOKIE")
  auth_headers+=(-H "Cookie: $COOKIE")
fi
cookie_args=()
if [[ -n "$COOKIE_JAR" ]]; then
  cookie_args=(-b "$COOKIE_JAR")
fi

fetch_json() {
  local path="$1"
  local output="$2"
  curl -fsS -X GET "${headers[@]}" "${cookie_args[@]}" "$BASE_URL$path" -o "$output"
}

fetch_status() {
  local path="$1"
  local output="$2"
  curl -sS -X GET "${headers[@]}" "${cookie_args[@]}" "$BASE_URL$path" -o "$output" -w "%{http_code}"
}

fail_if_contains() {
  local needle="$1"
  local label="$2"
  shift 2
  if [[ -z "$needle" ]]; then
    return 0
  fi
  local matches
  matches="$(grep -R -n -F -- "$needle" "$@" 2>/dev/null || true)"
  if [[ -n "$matches" ]]; then
    echo "Plaintext secret leaked in $label:" >&2
    echo "$matches" >&2
    exit 10
  fi
}

fail_if_forbidden_secret_shape() {
  local label="$1"
  shift
  local matches
  matches="$(grep -R -n -E '"secrets"[[:space:]]*:[[:space:]]*\{[[:space:]]*"[^"]+"' "$@" 2>/dev/null || true)"
  if [[ -n "$matches" ]]; then
    echo "User-facing response still contains non-empty secrets object in $label:" >&2
    echo "$matches" >&2
    exit 11
  fi
}

echo "[1/4] Fetch user-facing data source responses"
fetch_json "/api/infra/data-sources" "$OUT_DIR/01-data-source-list.json"
if [[ -n "$DATA_SOURCE_ID" ]]; then
  fetch_json "/api/infra/data-sources/$DATA_SOURCE_ID" "$OUT_DIR/02-data-source-detail.json"
  fetch_json "/api/infra/data-sources/$DATA_SOURCE_ID/detail" "$OUT_DIR/03-data-source-detail-redacted.json"

  echo "[2/4] Verify runtime-detail is not available to browser/user tokens"
  runtime_status="$(fetch_status "/api/infra/data-sources/$DATA_SOURCE_ID/runtime-detail" "$OUT_DIR/04-runtime-detail-user-token.json" || true)"
  echo "$runtime_status" > "$OUT_DIR/04-runtime-detail-user-token.status"
  if [[ "$runtime_status" =~ ^2 ]]; then
    echo "runtime-detail unexpectedly returned HTTP $runtime_status to a non-service token" >&2
    exit 12
  fi

  spoof_headers=(-H "Content-Type: application/json" -H "X-DTS-Service: dts-ingestion")
  if [[ -n "$COOKIE" ]]; then
    spoof_headers+=(-H "Cookie: $COOKIE")
  fi
  spoof_status="$(curl -sS -X GET "${spoof_headers[@]}" "${cookie_args[@]}" \
    "$BASE_URL/api/infra/data-sources/$DATA_SOURCE_ID/runtime-detail" \
    -o "$OUT_DIR/04b-runtime-detail-spoofed-service-no-token.json" -w "%{http_code}" || true)"
  echo "$spoof_status" > "$OUT_DIR/04b-runtime-detail-spoofed-service-no-token.status"
  if [[ "$spoof_status" =~ ^2 ]]; then
    echo "runtime-detail unexpectedly returned HTTP $spoof_status to X-DTS-Service without service token" >&2
    exit 13
  fi
else
  echo "[2/4] Runtime endpoint check skipped: DTS_DATA_SOURCE_ID is not set"
fi

echo "[3/4] Scan response payloads for plaintext secret sentinels and non-empty secrets objects"
fail_if_contains "$SECRET_SENTINEL" "API responses" "$OUT_DIR"
fail_if_forbidden_secret_shape "API responses" "$OUT_DIR/01-data-source-list.json" "$OUT_DIR/02-data-source-detail.json" "$OUT_DIR/03-data-source-detail-redacted.json"

echo "[4/4] Scan smoke/audit output directories when present"
if [[ -n "$AUDIT_EXPORT_QUERY" ]]; then
  audit_status="$(curl -sS -X GET "${auth_headers[@]}" \
    "${cookie_args[@]}" \
    "$BASE_URL/api/security/audit-logs/export?$AUDIT_EXPORT_QUERY" \
    -o "$OUT_DIR/04c-audit-export.csv" -w "%{http_code}" || true)"
  echo "$audit_status" > "$OUT_DIR/04c-audit-export.status"
  if [[ ! "$audit_status" =~ ^2 ]]; then
    echo "audit export returned HTTP $audit_status; check permissions or DTS_AUDIT_EXPORT_QUERY" >&2
    exit 14
  fi
else
  echo "audit export scan skipped: DTS_AUDIT_EXPORT_QUERY is not set" > "$OUT_DIR/04c-audit-export.status"
fi

IFS=':' read -r -a scan_dirs <<< "$SCAN_DIRS"
existing_dirs=()
for dir in "${scan_dirs[@]}"; do
  if [[ -d "$dir" ]]; then
    existing_dirs+=("$dir")
  fi
done
if [[ ${#existing_dirs[@]} -gt 0 ]]; then
  fail_if_contains "$SECRET_SENTINEL" "smoke/audit output files" "${existing_dirs[@]}"
fi

{
  echo "credential_redaction_audit=PASS"
  echo "base_url=$BASE_URL"
  echo "data_source_id=${DATA_SOURCE_ID:-N/A}"
  echo "runtime_detail_user_status=${runtime_status:-SKIPPED}"
  echo "runtime_detail_spoof_status=${spoof_status:-SKIPPED}"
  echo "audit_export_status=${audit_status:-SKIPPED}"
  echo "scan_dirs=${existing_dirs[*]:-N/A}"
} > "$OUT_DIR/05-redaction-summary.txt"

echo "Credential redaction audit outputs written to $OUT_DIR"

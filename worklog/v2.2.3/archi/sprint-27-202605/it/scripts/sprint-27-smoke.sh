#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
WEB_URL="${DTS_WEBAPP_URL:-http://127.0.0.1:3001}"
API_URL="${DTS_PLATFORM_API_URL:-http://127.0.0.1:18082/api}"
OUT_DIR="${DTS_SMOKE_OUT:-$ROOT_DIR/worklog/v2.2.3/sprint-27-202605/it/evidence/$(date +%Y%m%d-local)/sprint-27}"

mkdir -p "$OUT_DIR"

failures=0

check_page() {
  local name="$1"
  local path="$2"
  local status_file="$OUT_DIR/${name}.status.txt"
  local headers_file="$OUT_DIR/${name}.headers.txt"
  local status

  status="$(curl -sS -o /dev/null -D "$headers_file" -w "%{http_code}" "$WEB_URL$path" || true)"
  printf '%s\n' "$status" > "$status_file"
  if [[ "$status" =~ ^2 ]]; then
    printf 'PASS page %-28s %s\n' "$path" "$status"
  else
    printf 'FAIL page %-28s %s\n' "$path" "$status"
    failures=$((failures + 1))
  fi
}

check_api() {
  local name="$1"
  local path="$2"
  local status_file="$OUT_DIR/${name}.status.txt"
  local body_file="$OUT_DIR/${name}.body.json"
  local status

  status="$(curl -sS -o "$body_file" -w "%{http_code}" "$API_URL$path" || true)"
  printf '%s\n' "$status" > "$status_file"
  case "$status" in
    2*|401|403)
      printf 'PASS api  %-28s %s\n' "$path" "$status"
      ;;
    *)
      printf 'FAIL api  %-28s %s\n' "$path" "$status"
      failures=$((failures + 1))
      ;;
  esac
}

{
  printf 'Sprint-27 smoke\n'
  printf 'web=%s\n' "$WEB_URL"
  printf 'api=%s\n' "$API_URL"
  printf 'out=%s\n' "$OUT_DIR"
  printf 'startedAt=%s\n' "$(date -Is)"
} > "$OUT_DIR/00-summary.txt"

check_page "01-page-elt-console" "/explore/etl"
check_page "02-page-metrics-operations" "/metrics/operations"
check_page "03-page-events" "/ops/events"
check_page "04-page-audit-evidence" "/ops/audit-evidence"
check_page "05-page-release-governance" "/ops/release-governance"
check_page "06-page-semantic-overview" "/metrics/semantic"
check_page "07-page-semantic-subjects" "/metrics/semantic/subjects"
check_page "08-page-semantic-objects" "/metrics/semantic/objects"
check_page "09-page-semantic-metrics" "/metrics/semantic/metrics"
check_page "10-page-semantic-models" "/metrics/semantic/models"
check_page "11-page-semantic-publish" "/metrics/semantic/publish"
check_page "12-page-semantic-runs" "/metrics/semantic/runs"

check_api "21-api-sprint27-elt-console" "/platform/sprint27/elt-console?days=7&hours=24"
check_api "22-api-sprint27-metric-ops" "/platform/sprint27/metric-operations?hours=168&bucketHours=24"
check_api "23-api-sprint27-events" "/platform/sprint27/events-console?page=0&size=20"
check_api "24-api-sprint27-audit-evidence" "/platform/sprint27/audit-evidence"
check_api "25-api-sprint27-release-governance" "/platform/sprint27/release-governance"
check_api "26-api-semantic-workbench" "/semantic/workbench"
check_api "27-api-semantic-menu-diagnostics" "/semantic/menu-diagnostics"

{
  printf 'finishedAt=%s\n' "$(date -Is)"
  printf 'failures=%s\n' "$failures"
} >> "$OUT_DIR/00-summary.txt"

if [[ "$failures" -gt 0 ]]; then
  printf 'Sprint-27 smoke failed: %s failure(s). evidence=%s\n' "$failures" "$OUT_DIR" >&2
  exit 1
fi

printf 'Sprint-27 smoke passed. evidence=%s\n' "$OUT_DIR"

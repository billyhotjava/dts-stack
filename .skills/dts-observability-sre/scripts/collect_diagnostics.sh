#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$REPO_ROOT"

export DTS_DBT_HOST_PROJECT_DIR="${DTS_DBT_HOST_PROJECT_DIR:-${REPO_ROOT}/services/dts-dbt}"
export STACK_ROOT="${STACK_ROOT:-${REPO_ROOT}}"

ts="$(date +%Y%m%d-%H%M%S)"
out="reports/diagnostics/$ts"
mkdir -p "$out"

redact() {
  sed -E \
    -e 's/(Authorization: Bearer )[A-Za-z0-9._~+\/=-]+/\1<redacted>/g' \
    -e 's/(password|passwd|secret|token|access_token|refresh_token|client_secret)([=: ]+)[^[:space:]]+/\1\2<redacted>/Ig' \
    -e 's#(jdbc:[^:]+://[^/[:space:]]+:[0-9]+/[^?[:space:]]+\?[^[:space:]]*)#<jdbc-url-redacted>#g'
}

run_capture() {
  local name="$1"
  shift
  {
    echo "$ $*"
    "$@" 2>&1 || true
  } | redact > "$out/$name.txt"
}

run_capture git-status git status --short
run_capture disk df -h .

if command -v docker >/dev/null 2>&1; then
  run_capture docker-version docker --version
  run_capture compose-app-ps docker compose -f docker-compose-app.yml ps
  run_capture compose-app-config docker compose -f docker-compose-app.yml config
  run_capture compose-dev-config docker compose -f docker-compose.dev.yml config
  run_capture compose-legacy-config docker compose -f docker-compose.legacy.yml config
fi

if [ -d services/dts-dbt/target ]; then
  find services/dts-dbt/target -maxdepth 1 -type f \( -name 'run_results.json' -o -name 'manifest.json' -o -name 'catalog.json' \) -print > "$out/dbt-target-files.txt"
fi

find logs -maxdepth 2 -type f 2>/dev/null | tail -n 100 > "$out/log-files.txt" || true

echo "Diagnostics written to $out"

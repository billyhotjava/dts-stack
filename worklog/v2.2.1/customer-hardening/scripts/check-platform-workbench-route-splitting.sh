#!/usr/bin/env bash

set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
log_file="$(mktemp)"
trap 'rm -f "$log_file"' EXIT

cd "$workspace_dir"

pnpm -C source/dts-platform-webapp build >"$log_file" 2>&1 || {
  cat "$log_file"
  echo "platform-webapp build failed" >&2
  exit 1
}

if rg -q "pages/workbench/index.tsx is dynamically imported" "$log_file"; then
  cat "$log_file"
  echo "workbench route-splitting warning detected" >&2
  exit 1
fi

cat "$log_file"

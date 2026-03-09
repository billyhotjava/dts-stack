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

if rg -q "pages/sys/login/providers/login-provider.tsx is dynamically imported|pages/sys/error/components/ErrorLayout.tsx is dynamically imported|pages/sys/error/Page403.tsx is dynamically imported" "$log_file"; then
  cat "$log_file"
  echo "system route-splitting warnings detected" >&2
  exit 1
fi

cat "$log_file"

#!/usr/bin/env bash

set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
log_file="$(mktemp)"
trap 'rm -f "$log_file"' EXIT

cd "$workspace_dir"

mvn -f source/dts-platform/pom.xml -DskipTests compile >"$log_file" 2>&1 || {
  cat "$log_file"
  echo "compile failed" >&2
  exit 1
}

if rg -q "Dependency convergence error|DependencyConvergence failed" "$log_file"; then
  cat "$log_file"
  echo "dependency convergence warnings detected" >&2
  exit 1
fi

cat "$log_file"

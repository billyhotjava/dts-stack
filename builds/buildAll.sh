#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if [[ $# -eq 0 ]]; then
  exec "${SCRIPT_DIR}/dts-build.sh" -all
fi

exec "${SCRIPT_DIR}/dts-build.sh" "$@"

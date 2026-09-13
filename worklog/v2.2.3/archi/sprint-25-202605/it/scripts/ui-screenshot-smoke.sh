#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${DTS_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)}"
cd "$ROOT_DIR/source/dts-platform-webapp"

node "$ROOT_DIR/worklog/v2.2.3/sprint-25-202605/it/scripts/ui-screenshot-smoke.mjs"

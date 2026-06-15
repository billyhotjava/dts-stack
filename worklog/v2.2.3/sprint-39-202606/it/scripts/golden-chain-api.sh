#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/golden-chain-lib.sh"

CHAIN_KEY="${GOLDEN_CHAIN_KEY:-api-sprint-38-orders}"
SPRINT38_SCRIPT="${SPRINT38_SCRIPT:-worklog/v2.2.3/sprint-38-202606/it/scripts/api-end-to-end.sh}"

run_or_plan \
  "api" \
  "${CHAIN_KEY}" \
  "复用 Sprint-38 API 入湖安全基线；如需重新跑 API E2E，先执行 RUN_LIVE=1 ${SPRINT38_SCRIPT}" \
  "读取 /api/golden-chains/${CHAIN_KEY}，确认 API 入湖证据挂接到同一链路模型" \
  "保留 evidence JSON，供 IT-02 API 黄金链路验收使用"

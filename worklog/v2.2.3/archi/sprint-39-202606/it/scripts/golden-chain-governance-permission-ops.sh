#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/golden-chain-lib.sh"

CHAIN_KEY="${GOLDEN_CHAIN_KEY:-jdbc-orders-daily}"

run_or_plan \
  "governance-permission-ops" \
  "${CHAIN_KEY}" \
  "读取 /api/golden-chains/${CHAIN_KEY}，确认治理阻断返回业务可读 failureReason 与 nextAction" \
  "确认隐藏或无权链路详情按 404/fail-closed 处理，不泄露资产名称或密级字段" \
  "确认任务失败证据可通过 evidenceRef 回指运行实例、告警或补数入口" \
  "保留 evidence JSON，供 IT-04/IT-05/IT-06 验收使用"

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/golden-chain-lib.sh"

CHAIN_KEY="${GOLDEN_CHAIN_KEY:-jdbc-orders-daily}"

run_or_plan \
  "jdbc" \
  "${CHAIN_KEY}" \
  "读取 /api/golden-chains 列表，确认 JDBC 链路可见" \
  "读取 /api/golden-chains/${CHAIN_KEY} 详情，确认 SOURCE/INGESTION/ODS/MODEL/GOVERNANCE/RELEASE/CONSUMABLE 阶段快照" \
  "保留 evidence JSON，供 IT-01 JDBC 黄金链路验收使用"

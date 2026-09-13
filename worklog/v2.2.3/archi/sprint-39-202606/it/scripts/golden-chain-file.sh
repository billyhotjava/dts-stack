#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/golden-chain-lib.sh"

CHAIN_KEY="${GOLDEN_CHAIN_KEY:-file-orders-daily}"

run_or_plan \
  "file" \
  "${CHAIN_KEY}" \
  "读取 /api/golden-chains/${CHAIN_KEY}，确认加密文件入湖、ODS、治理和消费阶段快照" \
  "确认证据引用不暴露文件密钥、临时解密路径或源文件敏感字段" \
  "保留 evidence JSON，供 IT-03 file 黄金链路验收使用"

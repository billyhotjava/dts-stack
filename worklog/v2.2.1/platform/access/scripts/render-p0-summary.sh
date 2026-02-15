#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCESS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RAW_DIR="${ACCESS_DIR}/raw"
REPORT_MD="${ACCESS_DIR}/report/p0-regression-summary.md"

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --metrics <file>      input csv (default: latest p0-access-metrics-*.csv)
  --report <file>       markdown output (default: report/p0-regression-summary.md)
  -h, --help
USAGE
}

METRICS_CSV=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --metrics)
      METRICS_CSV="${2:-}"
      shift 2
      ;;
    --report)
      REPORT_MD="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "${METRICS_CSV}" ]]; then
  METRICS_CSV="$(ls -t "${RAW_DIR}"/p0-access-metrics-*.csv 2>/dev/null | head -n1 || true)"
fi
if [[ -z "${METRICS_CSV}" || ! -f "${METRICS_CSV}" ]]; then
  echo "metrics csv not found" >&2
  exit 1
fi

mkdir -p "$(dirname "${REPORT_MD}")"

read -r RUN_AT MODE ARCH HOURS TOTAL_TRIGGER TRIGGERED FAILED DAG404 TASKLOG404 RESULT NOTE INGESTION_LOG < <(
  awk -F, 'NR==2 {print $1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12}' "${METRICS_CSV}"
)

cat > "${REPORT_MD}" <<EOF_MD
# P0 回归摘要（数据接入中心）

- 数据文件：
  - $(basename "${METRICS_CSV}")
- 生成时间（UTC）：${RUN_AT}
- 环境：${MODE} / ${ARCH}
- 采样窗口：近 ${HOURS}h

## 指标

| 指标 | 值 |
| --- | --- |
| 触发总数 | ${TOTAL_TRIGGER} |
| 触发成功 | ${TRIGGERED} |
| 触发失败 | ${FAILED} |
| DAG 404 | ${DAG404} |
| TaskLog 404 | ${TASKLOG404} |
| 结论 | ${RESULT} |
| 备注 | ${NOTE} |

## 判定规则

- PASS: 触发总数 > 0 且 DAG 404=0 且 TaskLog 404=0 且 触发失败=0。
- FAIL: DAG 404>0 或 TaskLog 404>0。
- OBSERVED: 其他情况（例如样本不足）。

## 采样来源

- Ingestion 日志：${INGESTION_LOG}
EOF_MD

echo "report_md=${REPORT_MD}"

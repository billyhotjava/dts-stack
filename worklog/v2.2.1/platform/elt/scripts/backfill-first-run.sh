#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COLLECT_SCRIPT="${SCRIPT_DIR}/collect-evidence.sh"
STABILITY_MD="${PLATFORM_DIR}/stability-24h.md"

HOURS=4
TZ_NAME="Asia/Shanghai"
MODE="normal"
ARCH="$(uname -m 2>/dev/null || echo unknown)"
ALLOW_ARCH_OVERRIDE=0

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --hours <n>   Collect recent n hours before backfill (default: 4)
  --tz <zone>   Timezone label (default: Asia/Shanghai)
  --mode <m>    Runtime mode label (default: normal)
  --arch <a>    Architecture label (default: uname -m)
  --allow-arch-override
                Allow arch label != host arch
  -h, --help    Show help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --hours)
      HOURS="${2:-}"
      shift 2
      ;;
    --tz)
      TZ_NAME="${2:-}"
      shift 2
      ;;
    --mode)
      MODE="${2:-}"
      shift 2
      ;;
    --arch)
      ARCH="${2:-}"
      shift 2
      ;;
    --allow-arch-override)
      ALLOW_ARCH_OVERRIDE=1
      shift
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

if ! [[ "${HOURS}" =~ ^[0-9]+$ ]] || [[ "${HOURS}" -lt 1 ]]; then
  echo "--hours must be a positive integer" >&2
  exit 1
fi

if [[ ! -x "${COLLECT_SCRIPT}" ]]; then
  echo "collect script not found: ${COLLECT_SCRIPT}" >&2
  exit 1
fi

COLLECT_ARGS=(
  --hours "${HOURS}"
  --tz "${TZ_NAME}"
  --mode "${MODE}"
  --arch "${ARCH}"
  --write-md
  --output-md "${PLATFORM_DIR}/first-run-report.md"
)
if [[ "${ALLOW_ARCH_OVERRIDE}" -eq 1 ]]; then
  COLLECT_ARGS+=(--allow-arch-override)
fi
bash "${COLLECT_SCRIPT}" "${COLLECT_ARGS[@]}"

LATEST_SUMMARY="$(ls -t "${PLATFORM_DIR}/raw"/summary-*.txt 2>/dev/null | head -n1 || true)"
LATEST_HOURLY="$(ls -t "${PLATFORM_DIR}/raw"/hourly-metrics-*.csv 2>/dev/null | head -n1 || true)"
LATEST_FAILURE="$(ls -t "${PLATFORM_DIR}/raw"/failure-top-*.csv 2>/dev/null | head -n1 || true)"

if [[ -z "${LATEST_SUMMARY}" || -z "${LATEST_HOURLY}" || -z "${LATEST_FAILURE}" ]]; then
  echo "raw evidence files not found" >&2
  exit 1
fi

TOTAL_JOBS="$(awk -F, 'NR>1 && $1!="message" {s+=$2} END {print s+0}' "${LATEST_HOURLY}")"
SUCCESS_JOBS="$(awk -F, 'NR>1 && $1!="message" {s+=$3} END {print s+0}' "${LATEST_HOURLY}")"
FAILED_JOBS="$(awk -F, 'NR>1 && $1!="message" {s+=$4} END {print s+0}' "${LATEST_HOURLY}")"
SUCCESS_RATE="0"
if [[ "${TOTAL_JOBS}" -gt 0 ]]; then
  SUCCESS_RATE="$(awk -v ok="${SUCCESS_JOBS}" -v total="${TOTAL_JOBS}" 'BEGIN { printf "%.2f", (ok*100)/total }')"
fi

DAG_404="$(grep -E '^dag_404_count=' "${LATEST_SUMMARY}" | cut -d= -f2- || echo 0)"
TASKLOG_404="$(grep -E '^tasklog_404_count=' "${LATEST_SUMMARY}" | cut -d= -f2- || echo 0)"
RUN_AT_UTC="$(grep -E '^run_at_utc=' "${LATEST_SUMMARY}" | cut -d= -f2- || date -u +%Y%m%dT%H%M%SZ)"

TOP_FAILURES="$(awk -F, 'NR==1{next} NR<=6 && $1!="message" {printf "  - %s: %s\n", $1, $2}' "${LATEST_FAILURE}")"
[[ -z "${TOP_FAILURES}" ]] && TOP_FAILURES="  - 无失败分类数据"

REL_SUMMARY="${LATEST_SUMMARY#${PLATFORM_DIR}/}"
REL_HOURLY="${LATEST_HOURLY#${PLATFORM_DIR}/}"
REL_FAILURE="${LATEST_FAILURE#${PLATFORM_DIR}/}"

{
  echo
  echo "### Auto Backfill ${RUN_AT_UTC}"
  echo "- 观测窗口：最近 ${HOURS} 小时"
  echo "- 模式/架构：${MODE} / ${ARCH}"
  echo "- 总任务数：${TOTAL_JOBS}（成功 ${SUCCESS_JOBS} / 失败 ${FAILED_JOBS}，成功率 ${SUCCESS_RATE}%）"
  echo "- DAG 404：${DAG_404}；Task Log 404：${TASKLOG_404}"
  echo "- 失败分类 Top:"
  printf "%s\n" "${TOP_FAILURES}"
  echo "- 证据文件：\`${REL_SUMMARY}\`、\`${REL_HOURLY}\`、\`${REL_FAILURE}\`"
} >> "${STABILITY_MD}"

echo "first-run backfill appended to ${STABILITY_MD}"

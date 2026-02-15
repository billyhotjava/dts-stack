#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCESS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RAW_DIR="${ACCESS_DIR}/raw"
REPORT_DIR="${ACCESS_DIR}/report"

HOURS=24
STRICT=0
INGESTION_LOG="$(cd "${ACCESS_DIR}/../../../.." && pwd)/logs/dts-ingestion/app.log"
MATRIX_SPEC="${P0_MATRIX_SPEC:-normal:x86_64,legacy:aarch64,dev:x86_64}"

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --hours <n>            log scan window (default: 24)
  --ingestion-log <f>    ingestion app log path
  --matrix <spec>        comma-separated mode:arch pairs
                         default: normal:x86_64,legacy:aarch64,dev:x86_64
  --strict               exit non-zero when any row result != PASS
  -h, --help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --hours)
      HOURS="${2:-}"
      shift 2
      ;;
    --ingestion-log)
      INGESTION_LOG="${2:-}"
      shift 2
      ;;
    --matrix)
      MATRIX_SPEC="${2:-}"
      shift 2
      ;;
    --strict)
      STRICT=1
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

mkdir -p "${RAW_DIR}" "${REPORT_DIR}"

RUN_AT="$(date -u +"%Y%m%dT%H%M%SZ")"
MATRIX_CSV="${RAW_DIR}/p0-access-matrix-${RUN_AT}.csv"
MATRIX_MD="${REPORT_DIR}/p0-regression-matrix-summary.md"

echo "run_at,mode,arch,hours,total_trigger,triggered,failed,dag404,tasklog404,result,note,ingestion_log" > "${MATRIX_CSV}"

IFS=',' read -r -a PAIRS <<< "${MATRIX_SPEC}"
if [[ ${#PAIRS[@]} -eq 0 ]]; then
  echo "empty matrix spec" >&2
  exit 1
fi

for pair in "${PAIRS[@]}"; do
  mode="$(echo "${pair}" | cut -d: -f1 | xargs)"
  arch="$(echo "${pair}" | cut -d: -f2 | xargs)"
  if [[ -z "${mode}" || -z "${arch}" ]]; then
    echo "invalid matrix pair: ${pair}" >&2
    exit 1
  fi
  OUT="$(bash "${SCRIPT_DIR}/collect-p0-metrics.sh" \
    --hours "${HOURS}" \
    --mode "${mode}" \
    --arch "${arch}" \
    --ingestion-log "${INGESTION_LOG}" \
    --raw-dir "${RAW_DIR}" \
    --note "matrix-${RUN_AT}")"
  BASENAME="$(echo "${OUT}" | awk -F= '/^metrics_csv=/{print $2}' | tail -n1)"
  if [[ -z "${BASENAME}" ]]; then
    echo "failed to resolve metrics csv for ${pair}" >&2
    exit 1
  fi
  FILE="${RAW_DIR}/${BASENAME}"
  awk -F, 'NR==2 {print $0}' "${FILE}" >> "${MATRIX_CSV}"
done

TOTAL_ROWS="$(awk 'END{print NR-1}' "${MATRIX_CSV}")"
PASS_ROWS="$(awk -F, 'NR>1 && $10=="PASS" {c++} END{print c+0}' "${MATRIX_CSV}")"
FAIL_ROWS="$(awk -F, 'NR>1 && $10=="FAIL" {c++} END{print c+0}' "${MATRIX_CSV}")"
OBS_ROWS="$(awk -F, 'NR>1 && $10=="OBSERVED" {c++} END{print c+0}' "${MATRIX_CSV}")"
OVERALL="PASS"
if [[ "${FAIL_ROWS}" -gt 0 ]]; then
  OVERALL="FAIL"
elif [[ "${PASS_ROWS}" -lt "${TOTAL_ROWS}" ]]; then
  OVERALL="OBSERVED"
fi

{
  echo "# P0 回归矩阵摘要（数据接入中心）"
  echo
  echo "- 生成时间（UTC）：${RUN_AT}"
  echo "- 采样窗口：近 ${HOURS}h"
  echo "- Ingestion 日志：${INGESTION_LOG}"
  echo "- 矩阵规格：\`${MATRIX_SPEC}\`"
  echo "- 总结论：**${OVERALL}**"
  echo
  echo "## 汇总"
  echo
  echo "| 指标 | 值 |"
  echo "| --- | --- |"
  echo "| 行数 | ${TOTAL_ROWS} |"
  echo "| PASS | ${PASS_ROWS} |"
  echo "| FAIL | ${FAIL_ROWS} |"
  echo "| OBSERVED | ${OBS_ROWS} |"
  echo
  echo "## 明细"
  echo
  echo "| mode | arch | total_trigger | triggered | failed | dag404 | tasklog404 | result |"
  echo "| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |"
  awk -F, 'NR>1 {printf("| %s | %s | %s | %s | %s | %s | %s | %s |\n",$2,$3,$5,$6,$7,$8,$9,$10)}' "${MATRIX_CSV}"
  echo
  echo "## 判定规则"
  echo
  echo "- PASS: 所有行 result=PASS。"
  echo "- FAIL: 任一行 result=FAIL。"
  echo "- OBSERVED: 无 FAIL，但存在 OBSERVED。"
} > "${MATRIX_MD}"

echo "p0 matrix regression completed"
echo "- matrix_csv : ${MATRIX_CSV}"
echo "- report_md  : ${MATRIX_MD}"
echo "- overall    : ${OVERALL}"

if [[ "${STRICT}" -eq 1 && "${OVERALL}" != "PASS" ]]; then
  echo "strict mode failed: overall=${OVERALL}" >&2
  exit 2
fi

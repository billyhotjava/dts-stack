#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RAW_DIR="${PLATFORM_DIR}/raw"
STABILITY_MD="${PLATFORM_DIR}/stability-24h.md"
MATRIX_CSV="${RAW_DIR}/env-matrix.csv"

MODE="normal"
ARCH="$(uname -m 2>/dev/null || echo unknown)"
SUMMARY_FILE=""
RESULT="OBSERVED"
NOTE=""

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --summary <file>   Summary file from collect-evidence (default: latest)
  --mode <name>      legacy/normal/dev (default: normal)
  --arch <name>      x86_64/aarch64 (default: uname -m)
  --result <value>   PASS/FAIL/OBSERVED (default: OBSERVED)
  --note <text>      Additional note
  -h, --help         Show help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --summary)
      SUMMARY_FILE="${2:-}"
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
    --result)
      RESULT="${2:-}"
      shift 2
      ;;
    --note)
      NOTE="${2:-}"
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

if [[ -z "${SUMMARY_FILE}" ]]; then
  SUMMARY_FILE="$(ls -t "${RAW_DIR}"/summary-*.txt 2>/dev/null | head -n1 || true)"
fi
if [[ -z "${SUMMARY_FILE}" || ! -f "${SUMMARY_FILE}" ]]; then
  echo "summary file not found" >&2
  exit 1
fi

RUN_AT="$(grep -E '^run_at_utc=' "${SUMMARY_FILE}" | cut -d= -f2- || date -u +%Y%m%dT%H%M%SZ)"
DAG_404="$(grep -E '^dag_404_count=' "${SUMMARY_FILE}" | cut -d= -f2- || echo 0)"
TASKLOG_404="$(grep -E '^tasklog_404_count=' "${SUMMARY_FILE}" | cut -d= -f2- || echo 0)"
HOURLY_CSV_BASENAME="$(grep -E '^hourly_csv=' "${SUMMARY_FILE}" | cut -d= -f2- || true)"
HOURLY_CSV="${RAW_DIR}/${HOURLY_CSV_BASENAME}"

TOTAL=0
SUCCESS=0
FAILED=0
SUCCESS_RATE="0.00"
if [[ -f "${HOURLY_CSV}" ]]; then
  TOTAL="$(awk -F, 'NR>1 && $1!="message" {s+=$2} END {print s+0}' "${HOURLY_CSV}")"
  SUCCESS="$(awk -F, 'NR>1 && $1!="message" {s+=$3} END {print s+0}' "${HOURLY_CSV}")"
  FAILED="$(awk -F, 'NR>1 && $1!="message" {s+=$4} END {print s+0}' "${HOURLY_CSV}")"
  if [[ "${TOTAL}" -gt 0 ]]; then
    SUCCESS_RATE="$(awk -v ok="${SUCCESS}" -v total="${TOTAL}" 'BEGIN { printf "%.2f", (ok*100)/total }')"
  fi
fi

mkdir -p "${RAW_DIR}"
if [[ ! -f "${MATRIX_CSV}" ]]; then
  echo "run_at_utc,mode,arch,total,success,failed,success_rate,dag_404,tasklog_404,result,note" > "${MATRIX_CSV}"
fi

ESC_NOTE="${NOTE//,/; }"
echo "${RUN_AT},${MODE},${ARCH},${TOTAL},${SUCCESS},${FAILED},${SUCCESS_RATE},${DAG_404},${TASKLOG_404},${RESULT},${ESC_NOTE}" >> "${MATRIX_CSV}"

TMP_MD="$(mktemp)"
{
  echo ""
  echo "## 7. 环境矩阵（自动汇总）"
  echo "| Run UTC | Mode | Arch | Total | Success | Failed | Success Rate | DAG 404 | TaskLog 404 | Result | Note |"
  echo "|---|---|---|---:|---:|---:|---:|---:|---:|---|---|"
  awk -F, 'NR>1 {printf "| %s | %s | %s | %s | %s | %s | %s%% | %s | %s | %s | %s |\n", $1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11}' "${MATRIX_CSV}"
} > "${TMP_MD}"

if grep -q '^## 7\. 环境矩阵（自动汇总）' "${STABILITY_MD}"; then
  awk 'BEGIN{skip=0}
       /^## 7\. 环境矩阵（自动汇总）/{skip=1; next}
       { if(skip==0) print }
      ' "${STABILITY_MD}" > "${STABILITY_MD}.tmp"
  cat "${STABILITY_MD}.tmp" "${TMP_MD}" > "${STABILITY_MD}"
  rm -f "${STABILITY_MD}.tmp"
else
  cat "${TMP_MD}" >> "${STABILITY_MD}"
fi

rm -f "${TMP_MD}"

echo "env matrix updated"
echo "- matrix csv: ${MATRIX_CSV}"
echo "- stability : ${STABILITY_MD}"

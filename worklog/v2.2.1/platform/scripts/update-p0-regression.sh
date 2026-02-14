#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
V221_DIR="$(cd "${PLATFORM_DIR}/.." && pwd)"

ENV_MATRIX_CSV="${PLATFORM_DIR}/raw/env-matrix.csv"
P0_MATRIX_MD="${V221_DIR}/p0-regression-matrix.md"
P0_CHECKLIST_MD="${V221_DIR}/p0-regression-checklist.md"

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --env-matrix <file>  Input env matrix csv (default: platform/raw/env-matrix.csv)
  --p0-matrix <file>   Target p0-regression-matrix.md
  --p0-checklist <file> Target p0-regression-checklist.md
  -h, --help           Show help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-matrix)
      ENV_MATRIX_CSV="${2:-}"
      shift 2
      ;;
    --p0-matrix)
      P0_MATRIX_MD="${2:-}"
      shift 2
      ;;
    --p0-checklist)
      P0_CHECKLIST_MD="${2:-}"
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

if [[ ! -f "${ENV_MATRIX_CSV}" ]]; then
  echo "env matrix not found: ${ENV_MATRIX_CSV}" >&2
  exit 1
fi
if [[ ! -f "${P0_MATRIX_MD}" ]]; then
  echo "p0 matrix markdown not found: ${P0_MATRIX_MD}" >&2
  exit 1
fi
if [[ ! -f "${P0_CHECKLIST_MD}" ]]; then
  echo "p0 checklist markdown not found: ${P0_CHECKLIST_MD}" >&2
  exit 1
fi

RUN_AT="$(date -u +"%Y%m%dT%H%M%SZ")"

normalize_env_name() {
  local mode="$1"
  local arch="$2"
  local arch_label="${arch}"
  case "${arch}" in
    x86_64|amd64)
      arch_label="x86"
      ;;
    aarch64|arm64)
      arch_label="arm-kylin"
      ;;
  esac
  echo "${mode}-${arch_label}"
}

TMP_ROWS="$(mktemp)"
TMP_LATEST="$(mktemp)"
TMP_SECTION_MATRIX="$(mktemp)"
TMP_SECTION_CHECKLIST="$(mktemp)"
trap 'rm -f "${TMP_ROWS}" "${TMP_LATEST}" "${TMP_SECTION_MATRIX}" "${TMP_SECTION_CHECKLIST}"' EXIT

# Keep latest row per mode+arch (last write wins by file order)
awk -F, 'NR>1 {
  key=$2"|"$3
  row[key]=$0
  keys[key]=1
}
END {
  for (k in keys) print row[k]
}' "${ENV_MATRIX_CSV}" > "${TMP_LATEST}"

# Build auto rows from env matrix (latest mode+arch)
while IFS=',' read -r run_at mode arch total success failed success_rate dag404 tasklog404 result note; do
  [[ -z "${run_at}" ]] && continue
  env_name="$(normalize_env_name "${mode}" "${arch}")"

  c03="OBSERVED"
  c04="OBSERVED"
  if [[ "${dag404}" == "0" && "${tasklog404}" == "0" && "${total}" != "0" ]]; then
    c03="PASS"
    c04="PASS"
  elif [[ "${dag404}" != "0" || "${tasklog404}" != "0" ]]; then
    c03="FAIL"
    c04="FAIL"
  fi

  conclusion="${result}"
  if [[ "${result}" == "PASS" && "${c03}" == "PASS" && "${c04}" == "PASS" ]]; then
    conclusion="PASS"
  fi

  printf '| %s | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | run=%s, total=%s, success=%s, failed=%s, rate=%s%%, note=%s |\n' \
    "${env_name}" "MANUAL" "MANUAL" "${c03}" "${c04}" "MANUAL" "${conclusion}" "${run_at}" "${total}" "${success}" "${failed}" "${success_rate}" "${note}" >> "${TMP_ROWS}"
done < "${TMP_LATEST}"

if [[ ! -s "${TMP_ROWS}" ]]; then
  echo "no data rows generated from env matrix" >&2
  exit 1
fi

{
  echo
  echo "## 5. 自动汇总（来自 platform/raw/env-matrix.csv）"
  echo "- 生成时间(UTC)：${RUN_AT}"
  echo "- 说明：P0-C01/P0-C02/P0-C05 仍需现场业务回归，自动汇总仅根据执行指标回填 P0-C03/P0-C04 与基础结论。"
  echo
  echo "| 环境 | P0-C01 | P0-C02 | P0-C03 | P0-C04 | P0-C05 | 结论 | 备注 |"
  echo "| --- | --- | --- | --- | --- | --- | --- | --- |"
  sort "${TMP_ROWS}"
} > "${TMP_SECTION_MATRIX}"

if grep -q '^## 5\. 自动汇总（来自 platform/raw/env-matrix\.csv）' "${P0_MATRIX_MD}"; then
  awk 'BEGIN{skip=0}
       /^## 5\. 自动汇总（来自 platform\/raw\/env-matrix\.csv）/{skip=1; next}
       { if(skip==0) print }
      ' "${P0_MATRIX_MD}" > "${P0_MATRIX_MD}.tmp"
  cat "${P0_MATRIX_MD}.tmp" "${TMP_SECTION_MATRIX}" > "${P0_MATRIX_MD}"
  rm -f "${P0_MATRIX_MD}.tmp"
else
  cat "${TMP_SECTION_MATRIX}" >> "${P0_MATRIX_MD}"
fi

TOTAL_ALL="$(awk -F, '{s+=$4} END {print s+0}' "${TMP_LATEST}")"
SUCCESS_ALL="$(awk -F, '{s+=$5} END {print s+0}' "${TMP_LATEST}")"
FAILED_ALL="$(awk -F, '{s+=$6} END {print s+0}' "${TMP_LATEST}")"
DAG404_ALL="$(awk -F, '{s+=$8} END {print s+0}' "${TMP_LATEST}")"
TASKLOG404_ALL="$(awk -F, '{s+=$9} END {print s+0}' "${TMP_LATEST}")"

CHECK_DAG="[ ]"
CHECK_LOG="[ ]"
if [[ "${TOTAL_ALL}" -gt 0 && "${DAG404_ALL}" -eq 0 ]]; then
  CHECK_DAG="[x]"
fi
if [[ "${TOTAL_ALL}" -gt 0 && "${TASKLOG404_ALL}" -eq 0 ]]; then
  CHECK_LOG="[x]"
fi

{
  echo
  echo "## 自动回填（来自 platform/raw/env-matrix.csv）"
  echo "- 生成时间(UTC)：${RUN_AT}"
  echo "- 样本总任务（按 mode+arch 最新样本聚合）：${TOTAL_ALL}（成功 ${SUCCESS_ALL} / 失败 ${FAILED_ALL}）"
  echo "- DAG 404 总数：${DAG404_ALL}"
  echo "- TaskLog 404 总数：${TASKLOG404_ALL}"
  echo "- ${CHECK_DAG} DAG ready：自动样本中未出现 DAG 404"
  echo "- ${CHECK_LOG} 日志可读：自动样本中未出现 TaskLog 404"
  echo "- 说明：Excel/源库全量语义（C01/C02）与 ODS 一键生成（C05）仍需现场业务回归。"
} > "${TMP_SECTION_CHECKLIST}"

if grep -q '^## 自动回填（来自 platform/raw/env-matrix\.csv）' "${P0_CHECKLIST_MD}"; then
  awk 'BEGIN{skip=0}
       /^## 自动回填（来自 platform\/raw\/env-matrix\.csv）/{skip=1; next}
       { if(skip==0) print }
      ' "${P0_CHECKLIST_MD}" > "${P0_CHECKLIST_MD}.tmp"
  cat "${P0_CHECKLIST_MD}.tmp" "${TMP_SECTION_CHECKLIST}" > "${P0_CHECKLIST_MD}"
  rm -f "${P0_CHECKLIST_MD}.tmp"
else
  cat "${TMP_SECTION_CHECKLIST}" >> "${P0_CHECKLIST_MD}"
fi

echo "p0 regression docs updated"
echo "- matrix   : ${P0_MATRIX_MD}"
echo "- checklist: ${P0_CHECKLIST_MD}"

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RAW_DIR="${PLATFORM_DIR}/raw"
TARGET_DOC="${PLATFORM_DIR}/addax-airbyte-semantic-compare.md"

LEFT_FILE=""
RIGHT_FILE=""
KEY_COL=""
LEFT_LABEL="ADDAX"
RIGHT_LABEL="AIRBYTE"
APPEND_DOC=0

usage() {
  cat <<USAGE
Usage: $(basename "$0") --left <csv> --right <csv> --key <column> [options]

Options:
  --left-label <name>    Left dataset label (default: ADDAX)
  --right-label <name>   Right dataset label (default: AIRBYTE)
  --append-doc           Append summary section to addax-airbyte-semantic-compare.md
  -h, --help             Show help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --left)
      LEFT_FILE="${2:-}"
      shift 2
      ;;
    --right)
      RIGHT_FILE="${2:-}"
      shift 2
      ;;
    --key)
      KEY_COL="${2:-}"
      shift 2
      ;;
    --left-label)
      LEFT_LABEL="${2:-}"
      shift 2
      ;;
    --right-label)
      RIGHT_LABEL="${2:-}"
      shift 2
      ;;
    --append-doc)
      APPEND_DOC=1
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

if [[ -z "${LEFT_FILE}" || -z "${RIGHT_FILE}" || -z "${KEY_COL}" ]]; then
  usage
  exit 1
fi
if [[ ! -f "${LEFT_FILE}" || ! -f "${RIGHT_FILE}" ]]; then
  echo "left/right csv not found" >&2
  exit 1
fi

mkdir -p "${RAW_DIR}"
RUN_AT="$(date -u +"%Y%m%dT%H%M%SZ")"

left_key_idx="$(awk -F, -v key="${KEY_COL}" 'NR==1{for(i=1;i<=NF;i++){if($i==key){print i; exit}}}' "${LEFT_FILE}")"
right_key_idx="$(awk -F, -v key="${KEY_COL}" 'NR==1{for(i=1;i<=NF;i++){if($i==key){print i; exit}}}' "${RIGHT_FILE}")"
if [[ -z "${left_key_idx}" || -z "${right_key_idx}" ]]; then
  echo "key column not found in one of csv files: ${KEY_COL}" >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

left_norm="${tmp_dir}/left.norm"
right_norm="${tmp_dir}/right.norm"

# first-pass: use CSV split by comma (sufficient for current generated evidence csv)
awk -F, -v idx="${left_key_idx}" 'NR>1{key=$idx; if(key=="") next; row=$0; print key"|"row}' "${LEFT_FILE}" | sort > "${left_norm}"
awk -F, -v idx="${right_key_idx}" 'NR>1{key=$idx; if(key=="") next; row=$0; print key"|"row}' "${RIGHT_FILE}" | sort > "${right_norm}"

cut -d'|' -f1 "${left_norm}" > "${tmp_dir}/left.keys"
cut -d'|' -f1 "${right_norm}" > "${tmp_dir}/right.keys"

comm -23 "${tmp_dir}/left.keys" "${tmp_dir}/right.keys" > "${tmp_dir}/only_left.keys"
comm -13 "${tmp_dir}/left.keys" "${tmp_dir}/right.keys" > "${tmp_dir}/only_right.keys"

join -t '|' -1 1 -2 1 "${left_norm}" "${right_norm}" | awk -F'|' '$2!=$3{print $1}' > "${tmp_dir}/mismatch.keys"

LEFT_ROWS="$(wc -l < "${left_norm}" | tr -d ' ')"
RIGHT_ROWS="$(wc -l < "${right_norm}" | tr -d ' ')"
ONLY_LEFT="$(wc -l < "${tmp_dir}/only_left.keys" | tr -d ' ')"
ONLY_RIGHT="$(wc -l < "${tmp_dir}/only_right.keys" | tr -d ' ')"
MISMATCH="$(wc -l < "${tmp_dir}/mismatch.keys" | tr -d ' ')"
COMMON="$(( LEFT_ROWS - ONLY_LEFT ))"

RESULT="PASS"
if [[ "${ONLY_LEFT}" -gt 0 || "${ONLY_RIGHT}" -gt 0 || "${MISMATCH}" -gt 0 ]]; then
  RESULT="FAIL"
fi

REPORT_FILE="${RAW_DIR}/semantic-compare-${RUN_AT}.md"
{
  echo "# Semantic Compare ${RUN_AT}"
  echo
  echo "- 左侧：\`${LEFT_LABEL}\` (\`${LEFT_FILE}\`)"
  echo "- 右侧：\`${RIGHT_LABEL}\` (\`${RIGHT_FILE}\`)"
  echo "- 对比主键列：\`${KEY_COL}\`"
  echo "- 结果：**${RESULT}**"
  echo
  echo "## 指标"
  echo "- ${LEFT_LABEL} 行数：${LEFT_ROWS}"
  echo "- ${RIGHT_LABEL} 行数：${RIGHT_ROWS}"
  echo "- 共同主键数：${COMMON}"
  echo "- 仅 ${LEFT_LABEL} 存在：${ONLY_LEFT}"
  echo "- 仅 ${RIGHT_LABEL} 存在：${ONLY_RIGHT}"
  echo "- 同主键数据不一致：${MISMATCH}"
  echo
  echo "## 详情样本"
  echo "- only-left keys (top 20):"
  head -n 20 "${tmp_dir}/only_left.keys" | sed 's/^/  - /'
  echo "- only-right keys (top 20):"
  head -n 20 "${tmp_dir}/only_right.keys" | sed 's/^/  - /'
  echo "- mismatch keys (top 20):"
  head -n 20 "${tmp_dir}/mismatch.keys" | sed 's/^/  - /'
} > "${REPORT_FILE}"

if [[ "${APPEND_DOC}" -eq 1 ]]; then
  {
    echo
    echo "## 自动对照 ${RUN_AT}"
    echo "- 输入：\`${LEFT_LABEL}\` vs \`${RIGHT_LABEL}\`，key=\`${KEY_COL}\`"
    echo "- 结果：${RESULT}（only-left=${ONLY_LEFT}, only-right=${ONLY_RIGHT}, mismatch=${MISMATCH}）"
    echo "- 报告：\`raw/$(basename "${REPORT_FILE}")\`"
  } >> "${TARGET_DOC}"
fi

echo "semantic compare completed"
echo "- result: ${RESULT}"
echo "- report: ${REPORT_FILE}"

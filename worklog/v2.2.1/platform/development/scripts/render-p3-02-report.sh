#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RAW_DIR="${DEV_DIR}/raw"
REPORT_DIR="${DEV_DIR}/report"
OUT_MD="${REPORT_DIR}/p3-02-matrix-latest.md"

mkdir -p "${RAW_DIR}" "${REPORT_DIR}"

latest_summary="$(ls -t "${RAW_DIR}"/p3-02-summary-*.txt 2>/dev/null | head -n 1 || true)"
if [[ -z "${latest_summary}" ]]; then
  echo "No p3-02 summary found under ${RAW_DIR}" >&2
  exit 1
fi

now_utc="$(date -u +%Y%m%dT%H%M%SZ)"
trend_tsv="${RAW_DIR}/p3-02-trend-${now_utc}.tsv"
echo -e "run_at_utc\tmode\tarch\thours\thas_failure_category\tdag_404_count\ttasklog_404_count" > "${trend_tsv}"

while IFS= read -r file; do
  run_at="$(awk -F= '$1=="run_at_utc"{print $2}' "${file}" | tail -n 1)"
  mode="$(awk -F= '$1=="mode"{print $2}' "${file}" | tail -n 1)"
  arch="$(awk -F= '$1=="arch"{print $2}' "${file}" | tail -n 1)"
  hours="$(awk -F= '$1=="hours"{print $2}' "${file}" | tail -n 1)"
  has_failure="$(awk -F= '$1=="has_failure_category"{print $2}' "${file}" | tail -n 1)"
  dag404="$(awk -F= '$1=="dag_404_count"{print $2}' "${file}" | tail -n 1)"
  task404="$(awk -F= '$1=="tasklog_404_count"{print $2}' "${file}" | tail -n 1)"
  echo -e "${run_at}\t${mode}\t${arch}\t${hours}\t${has_failure}\t${dag404}\t${task404}" >> "${trend_tsv}"
done < <(ls -1 "${RAW_DIR}"/p3-02-summary-*.txt 2>/dev/null | sort)

latest_run_at="$(awk -F= '$1=="run_at_utc"{print $2}' "${latest_summary}" | tail -n 1)"
latest_mode="$(awk -F= '$1=="mode"{print $2}' "${latest_summary}" | tail -n 1)"
latest_arch="$(awk -F= '$1=="arch"{print $2}' "${latest_summary}" | tail -n 1)"
latest_hours="$(awk -F= '$1=="hours"{print $2}' "${latest_summary}" | tail -n 1)"
latest_has_failure="$(awk -F= '$1=="has_failure_category"{print $2}' "${latest_summary}" | tail -n 1)"
latest_failure_rows=0
failure_collect_failed=0
hourly_collect_failed=0

latest_failure_csv="${RAW_DIR}/p3-02-failure-top-${latest_run_at}.csv"
latest_hourly_csv="${RAW_DIR}/p3-02-hourly-${latest_run_at}.csv"
failure_top_tsv="${RAW_DIR}/p3-02-failure-topn-${latest_run_at}.tsv"

if [[ -f "${latest_failure_csv}" ]]; then
  if awk -F, 'NR>1 && $1=="collect_failed"{found=1} END{exit found?0:1}' "${latest_failure_csv}"; then
    failure_collect_failed=1
  fi
  awk -F, '
    NR==1 {print "failure_category\tfailed_count"; next}
    $1=="message" || $1=="collect_failed" {next}
    NF>=2 && $1!="" && $2!="" {print $1 "\t" $2}
  ' "${latest_failure_csv}" > "${failure_top_tsv}"
  latest_failure_rows="$(awk -F, 'NR>1 && $1!="message" && $1!="collect_failed" && NF>=2 && $1!="" && $2!="" {count++} END {print count+0}' "${latest_failure_csv}")"
else
  echo -e "failure_category\tfailed_count" > "${failure_top_tsv}"
fi

if [[ -f "${latest_hourly_csv}" ]]; then
  if awk -F, 'NR>1 && $1=="collect_failed"{found=1} END{exit found?0:1}' "${latest_hourly_csv}"; then
    hourly_collect_failed=1
  fi
fi

{
  echo "# P3-02 多环境多架构回归矩阵（最新）"
  echo
  echo "生成时间(UTC): ${now_utc}"
  echo
  echo "## 最新快照"
  echo
  echo "- run_at_utc: ${latest_run_at}"
  echo "- mode: ${latest_mode}"
  echo "- arch: ${latest_arch}"
  echo "- hours: ${latest_hours}"
  echo "- has_failure_category(flag): ${latest_has_failure}"
  echo "- failure_rows: ${latest_failure_rows}"
  echo "- failure_collect_failed: ${failure_collect_failed}"
  echo "- hourly_collect_failed: ${hourly_collect_failed}"
  echo
  echo "## 失败 TopN（最新）"
  echo
  if [[ "${failure_collect_failed}" -eq 1 ]]; then
    echo "失败分类采集失败（collect_failed），请检查 Postgres 查询权限或 SQL 兼容性。"
  elif [[ -s "${failure_top_tsv}" ]] && [[ "$(wc -l < "${failure_top_tsv}")" -gt 1 ]]; then
    echo "| failure_category | failed_count |"
    echo "|---|---:|"
    awk -F'\t' 'NR>1{printf("| %s | %s |\n",$1,$2)}' "${failure_top_tsv}"
  else
    echo "无失败分类记录（0 rows）。"
  fi
  echo
  echo "## 趋势（summary 汇总）"
  echo
  echo "| run_at_utc | mode | arch | hours | has_failure_category | dag_404_count | tasklog_404_count |"
  echo "|---|---|---|---:|---:|---:|---:|"
  awk -F'\t' 'NR>1{printf("| %s | %s | %s | %s | %s | %s | %s |\n",$1,$2,$3,$4,$5,$6,$7)}' "${trend_tsv}"
  echo
  echo "## 小时明细（最新）"
  echo
  if [[ -f "${latest_hourly_csv}" ]]; then
    if [[ "${hourly_collect_failed}" -eq 1 ]]; then
      echo "小时明细采集失败（collect_failed），请检查 Postgres 查询权限或 SQL 兼容性。"
    else
      echo "| hour_slot | total | success | failed | avg_seconds | p95_seconds |"
      echo "|---|---:|---:|---:|---:|---:|"
      awk -F, 'NR>1 && $1!="message" && $1!="collect_failed"{printf("| %s | %s | %s | %s | %s | %s |\n",$1,$2,$3,$4,$5,$6)}' "${latest_hourly_csv}"
    fi
  else
    echo "未找到 hourly 明细文件。"
  fi
  echo
  echo "## 产物索引"
  echo
  echo "- summary: \`${latest_summary}\`"
  echo "- failure top: \`${failure_top_tsv}\`"
  echo "- trend: \`${trend_tsv}\`"
  [[ -f "${latest_hourly_csv}" ]] && echo "- hourly: \`${latest_hourly_csv}\`"
} > "${OUT_MD}"

echo "Generated report: ${OUT_MD}"

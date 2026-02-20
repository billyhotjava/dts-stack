#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${PLATFORM_DIR}/../../.." && pwd)"
RAW_DIR="${PLATFORM_DIR}/raw"

HOURS=24
TZ_NAME="Asia/Shanghai"
MODE="normal"
HOST_ARCH="$(uname -m 2>/dev/null || echo unknown)"
ARCH="${HOST_ARCH}"
ALLOW_ARCH_OVERRIDE=0
WRITE_MD=0
OUTPUT_MD="${PLATFORM_DIR}/first-run-report.md"

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --hours <n>         Collect recent n hours (default: 24)
  --tz <zone>         Timezone label used in report (default: Asia/Shanghai)
  --mode <name>       Runtime mode label: legacy/normal/dev (default: normal)
  --arch <name>       Architecture label (default: uname -m)
  --allow-arch-override
                      Allow arch label != host arch (default: disallow)
  --write-md          Write first-run markdown report
  --output-md <path>  Markdown output path (default: platform/first-run-report.md)
  -h, --help          Show this help
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
    --write-md)
      WRITE_MD=1
      shift
      ;;
    --output-md)
      OUTPUT_MD="${2:-}"
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

if ! [[ "${HOURS}" =~ ^[0-9]+$ ]] || [[ "${HOURS}" -lt 1 ]]; then
  echo "--hours must be a positive integer" >&2
  exit 1
fi

if [[ "${ARCH}" != "${HOST_ARCH}" && "${ALLOW_ARCH_OVERRIDE}" -ne 1 ]]; then
  echo "arch mismatch: requested='${ARCH}', host='${HOST_ARCH}'" >&2
  echo "use --allow-arch-override if you intentionally need label override" >&2
  exit 1
fi

mkdir -p "${RAW_DIR}"

# Use pid suffix to avoid filename collision when multiple runs happen in same second.
RUN_AT="$(date -u +"%Y%m%dT%H%M%SZ")-$$"
START_UTC="$(date -u -d "-${HOURS} hours" +"%Y-%m-%d %H:%M:%S+00")"
SINCE_ISO="$(date -u -d "-${HOURS} hours" +"%Y-%m-%dT%H:%M:%SZ")"

ENV_FILE="${REPO_DIR}/.env"
PG_DB="dts_platform"
PG_USER="dts_platform"
PG_PWD=""
if [[ -f "${ENV_FILE}" ]]; then
  PG_DB="$(grep -E '^PG_DB_DTPS=' "${ENV_FILE}" | head -n1 | cut -d= -f2- || true)"
  PG_USER="$(grep -E '^PG_USER_DTPS=' "${ENV_FILE}" | head -n1 | cut -d= -f2- || true)"
  PG_PWD="$(grep -E '^PG_PWD_DTPS=' "${ENV_FILE}" | head -n1 | cut -d= -f2- || true)"
  [[ -z "${PG_DB}" ]] && PG_DB="dts_platform"
  [[ -z "${PG_USER}" ]] && PG_USER="dts_platform"
fi

find_pg_container() {
  docker ps --format '{{.Names}}' 2>/dev/null | grep -E '(^|[-_])(dts-pg|postgres)([-_]|$)' | head -n1 || true
}

PG_CONTAINER="$(find_pg_container)"
INGESTION_CONTAINER="$(docker ps --format '{{.Names}}' 2>/dev/null | grep -E '(^|[-_])dts-ingestion([-_]|$)' | head -n1 || true)"

query_csv_via_docker() {
  local sql="$1"
  local outfile="$2"
  if [[ -z "${PG_CONTAINER}" ]]; then
    return 1
  fi
  docker exec -e PGPASSWORD="${PG_PWD}" "${PG_CONTAINER}" \
    psql -U "${PG_USER}" -d "${PG_DB}" -v ON_ERROR_STOP=1 -c "COPY (${sql}) TO STDOUT WITH CSV HEADER" > "${outfile}"
}

query_scalar_via_docker() {
  local sql="$1"
  if [[ -z "${PG_CONTAINER}" ]]; then
    return 1
  fi
  docker exec -e PGPASSWORD="${PG_PWD}" "${PG_CONTAINER}" \
    psql -U "${PG_USER}" -d "${PG_DB}" -v ON_ERROR_STOP=1 -At -c "${sql}"
}

table_exists() {
  local table_name="$1"
  local exists
  exists="$(query_scalar_via_docker "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='${table_name}') THEN 1 ELSE 0 END" || echo 0)"
  [[ "${exists}" == "1" ]]
}

safe_query_csv() {
  local sql="$1"
  local outfile="$2"
  local errfile="${outfile}.err"
  if ! query_csv_via_docker "${sql}" "${outfile}" 2>"${errfile}"; then
    local errline
    errline="$(tail -n 1 "${errfile}" 2>/dev/null || true)"
    echo "warning: failed to collect ${outfile} (postgres unavailable or query failed) ${errline}" >&2
    printf "message\ncollect_failed\n" > "${outfile}"
  else
    rm -f "${errfile}"
  fi
}

HOURLY_CSV="${RAW_DIR}/hourly-metrics-${RUN_AT}.csv"
FAILURE_CSV="${RAW_DIR}/failure-top-${RUN_AT}.csv"

HAS_INGESTION_EXECUTION=0
HAS_INFRA_RUN_LOG=0
if table_exists "ingestion_execution"; then
  HAS_INGESTION_EXECUTION=1
fi
if table_exists "infra_external_run_log"; then
  HAS_INFRA_RUN_LOG=1
fi

METRIC_SOURCE_TABLE="unavailable"
if [[ "${HAS_INGESTION_EXECUTION}" == "1" ]]; then
  METRIC_SOURCE_TABLE="ingestion_execution"
elif [[ "${HAS_INFRA_RUN_LOG}" == "1" ]]; then
  METRIC_SOURCE_TABLE="infra_external_run_log"
fi

if [[ "${METRIC_SOURCE_TABLE}" == "ingestion_execution" ]]; then
HOURLY_SQL="
SELECT
  to_char(date_trunc('hour', created_at AT TIME ZONE '${TZ_NAME}'), 'YYYY-MM-DD HH24:00') AS hour_slot,
  COUNT(*) AS total,
  SUM(CASE WHEN lower(coalesce(status,'')) = 'success' THEN 1 ELSE 0 END) AS success,
  SUM(CASE WHEN lower(coalesce(status,'')) = 'failed' THEN 1 ELSE 0 END) AS failed,
  ROUND(AVG(EXTRACT(EPOCH FROM (COALESCE(end_time, created_at) - COALESCE(start_time, created_at))))::numeric, 2) AS avg_seconds,
  ROUND(
    PERCENTILE_CONT(0.95) WITHIN GROUP (
      ORDER BY EXTRACT(EPOCH FROM (COALESCE(end_time, created_at) - COALESCE(start_time, created_at)))
    )::numeric,
    2
  ) AS p95_seconds
FROM ingestion_execution
WHERE created_at >= TIMESTAMPTZ '${START_UTC}'
GROUP BY 1
ORDER BY 1
"

HAS_FAILURE_CATEGORY="$(query_scalar_via_docker "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='ingestion_execution' AND column_name='failure_category') THEN 1 ELSE 0 END" || echo 0)"
if [[ "${HAS_FAILURE_CATEGORY}" == "1" ]]; then
  FAILURE_SQL="
SELECT
  COALESCE(NULLIF(TRIM(failure_category), ''), 'UNCLASSIFIED') AS failure_category,
  COUNT(*) AS failed_count
FROM ingestion_execution
WHERE created_at >= TIMESTAMPTZ '${START_UTC}'
  AND lower(coalesce(status,'')) = 'failed'
GROUP BY 1
ORDER BY failed_count DESC, failure_category ASC
LIMIT 20
"
else
  FAILURE_SQL="
SELECT
  'UNCLASSIFIED' AS failure_category,
  COUNT(*) AS failed_count
FROM ingestion_execution
WHERE created_at >= TIMESTAMPTZ '${START_UTC}'
  AND lower(coalesce(status,'')) = 'failed'
GROUP BY 1
"
fi
elif [[ "${METRIC_SOURCE_TABLE}" == "infra_external_run_log" ]]; then
HOURLY_SQL="
SELECT
  to_char(date_trunc('hour', COALESCE(started_at, finished_at) AT TIME ZONE '${TZ_NAME}'), 'YYYY-MM-DD HH24:00') AS hour_slot,
  COUNT(*) AS total,
  SUM(CASE WHEN lower(coalesce(status,'')) IN ('success','succeeded','finished') THEN 1 ELSE 0 END) AS success,
  SUM(CASE WHEN lower(coalesce(status,'')) IN ('failed','fail','error','timeout','killed','cancelled') THEN 1 ELSE 0 END) AS failed,
  ROUND(AVG((COALESCE(duration_ms, 0)::numeric) / 1000), 2) AS avg_seconds,
  ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY (COALESCE(duration_ms, 0)::numeric) / 1000), 2) AS p95_seconds
FROM infra_external_run_log
WHERE COALESCE(started_at, finished_at) >= TIMESTAMPTZ '${START_UTC}'
  AND upper(coalesce(entry_key,'')) IN ('INGESTION_TASK','AIRFLOW_DAG','DBT_RUN')
GROUP BY 1
ORDER BY 1
"

HAS_FAILURE_CATEGORY=0
FAILURE_SQL="
SELECT
  COALESCE(NULLIF(TRIM(upper(status)), ''), 'UNCLASSIFIED') AS failure_category,
  COUNT(*) AS failed_count
FROM infra_external_run_log
WHERE COALESCE(started_at, finished_at) >= TIMESTAMPTZ '${START_UTC}'
  AND lower(coalesce(status,'')) IN ('failed','fail','error','timeout','killed','cancelled')
  AND upper(coalesce(entry_key,'')) IN ('INGESTION_TASK','AIRFLOW_DAG','DBT_RUN')
GROUP BY 1
ORDER BY failed_count DESC, failure_category ASC
LIMIT 20
"
else
HAS_FAILURE_CATEGORY=0
HOURLY_SQL=""
FAILURE_SQL=""
fi

if [[ -n "${HOURLY_SQL}" ]]; then
  safe_query_csv "${HOURLY_SQL}" "${HOURLY_CSV}"
else
  printf "message\ncollect_failed\n" > "${HOURLY_CSV}"
  echo "missing_source_table=ingestion_execution|infra_external_run_log" > "${HOURLY_CSV}.err"
fi

if [[ -n "${FAILURE_SQL}" ]]; then
  safe_query_csv "${FAILURE_SQL}" "${FAILURE_CSV}"
else
  printf "message\ncollect_failed\n" > "${FAILURE_CSV}"
  echo "missing_source_table=ingestion_execution|infra_external_run_log" > "${FAILURE_CSV}.err"
fi

HOURLY_COLLECT_FAILED=0
FAILURE_COLLECT_FAILED=0
[[ -f "${HOURLY_CSV}" ]] && awk -F, 'NR>1 && $1=="collect_failed"{found=1} END{exit found?0:1}' "${HOURLY_CSV}" && HOURLY_COLLECT_FAILED=1 || true
[[ -f "${FAILURE_CSV}" ]] && awk -F, 'NR>1 && $1=="collect_failed"{found=1} END{exit found?0:1}' "${FAILURE_CSV}" && FAILURE_COLLECT_FAILED=1 || true

count_log_pattern() {
  local pattern="$1"
  if [[ -z "${INGESTION_CONTAINER}" ]]; then
    echo 0
    return 0
  fi
  docker logs --since "${SINCE_ISO}" "${INGESTION_CONTAINER}" 2>&1 | (grep -E "${pattern}" || true) | wc -l | tr -d ' '
}

DAG_404_COUNT="$(count_log_pattern 'DAG with dag_id: .* not found|DAG 未就绪')"
TASKLOG_404_COUNT="$(count_log_pattern 'TaskInstance not found|task log fetch failed status=404')"

SUMMARY_TXT="${RAW_DIR}/summary-${RUN_AT}.txt"
{
  echo "run_at_utc=${RUN_AT}"
  echo "hours=${HOURS}"
  echo "timezone=${TZ_NAME}"
  echo "mode=${MODE}"
  echo "host_arch=${HOST_ARCH}"
  echo "arch=${ARCH}"
  echo "arch_override_allowed=${ALLOW_ARCH_OVERRIDE}"
  echo "pg_container=${PG_CONTAINER:-N/A}"
  echo "ingestion_container=${INGESTION_CONTAINER:-N/A}"
  echo "metric_source_table=${METRIC_SOURCE_TABLE}"
  echo "has_failure_category=${HAS_FAILURE_CATEGORY}"
  echo "dag_404_count=${DAG_404_COUNT}"
  echo "tasklog_404_count=${TASKLOG_404_COUNT}"
  echo "hourly_csv=$(basename "${HOURLY_CSV}")"
  echo "failure_csv=$(basename "${FAILURE_CSV}")"
  echo "hourly_collect_failed=${HOURLY_COLLECT_FAILED}"
  echo "failure_collect_failed=${FAILURE_COLLECT_FAILED}"
  [[ -f "${HOURLY_CSV}.err" ]] && echo "hourly_collect_error=$(basename "${HOURLY_CSV}.err")"
  [[ -f "${FAILURE_CSV}.err" ]] && echo "failure_collect_error=$(basename "${FAILURE_CSV}.err")"
} > "${SUMMARY_TXT}"

if [[ "${WRITE_MD}" -eq 1 ]]; then
  TOTAL_JOBS="$(awk -F, 'NR>1 && $1!="message" {s+=$2} END {print s+0}' "${HOURLY_CSV}")"
  SUCCESS_JOBS="$(awk -F, 'NR>1 && $1!="message" {s+=$3} END {print s+0}' "${HOURLY_CSV}")"
  FAILED_JOBS="$(awk -F, 'NR>1 && $1!="message" {s+=$4} END {print s+0}' "${HOURLY_CSV}")"
  SUCCESS_RATE="0"
  if [[ "${TOTAL_JOBS}" -gt 0 ]]; then
    SUCCESS_RATE="$(awk -v ok="${SUCCESS_JOBS}" -v total="${TOTAL_JOBS}" 'BEGIN { printf "%.2f", (ok*100)/total }')"
  fi

  TOP_FAILURES="$(awk -F, 'NR==1{next} NR<=6 && $1!="message" {printf "- %s: %s\n", $1, $2}' "${FAILURE_CSV}")"
  [[ -z "${TOP_FAILURES}" ]] && TOP_FAILURES="- 无失败分类数据"

  REL_HOURLY="${HOURLY_CSV#${PLATFORM_DIR}/}"
  REL_FAILURE="${FAILURE_CSV#${PLATFORM_DIR}/}"
  REL_SUMMARY="${SUMMARY_TXT#${PLATFORM_DIR}/}"

  {
    echo "# Platform 首轮实测回填报告"
    echo
    echo "- 生成时间(UTC)：${RUN_AT}"
    echo "- 观测窗口：最近 ${HOURS} 小时"
    echo "- 模式：${MODE}"
    echo "- 架构：${ARCH}"
    echo "- 时区标签：${TZ_NAME}"
    echo
    echo "## 关键指标"
    echo "- 总任务数：${TOTAL_JOBS}"
    echo "- 成功数：${SUCCESS_JOBS}"
    echo "- 失败数：${FAILED_JOBS}"
    echo "- 成功率：${SUCCESS_RATE}%"
    echo "- DAG 404 次数：${DAG_404_COUNT}"
    echo "- Task Log 404 次数：${TASKLOG_404_COUNT}"
    echo
    echo "## 失败分类 Top"
    echo "${TOP_FAILURES}"
    echo
    echo "## 原始数据"
    echo "- 小时指标："
    echo "  \`${REL_HOURLY}\`"
    echo "- 失败分类："
    echo "  \`${REL_FAILURE}\`"
    echo "- 采集摘要："
    echo "  \`${REL_SUMMARY}\`"
    echo
    echo "## 结论（自动草稿）"
    if [[ "${TOTAL_JOBS}" -eq 0 ]]; then
      echo "- 当前窗口未采集到执行记录，请确认任务是否触发。"
    elif [[ "${FAILED_JOBS}" -eq 0 ]]; then
      echo "- 当前窗口无失败任务，建议继续扩大窗口到 24h 观察。"
    else
      echo "- 当前窗口存在失败任务，建议优先处理失败分类 Top 项并复测。"
    fi
  } > "${OUTPUT_MD}"
fi

echo "evidence collection completed"
echo "- summary: ${SUMMARY_TXT}"
echo "- hourly : ${HOURLY_CSV}"
echo "- failure: ${FAILURE_CSV}"
if [[ "${WRITE_MD}" -eq 1 ]]; then
  echo "- report : ${OUTPUT_MD}"
fi

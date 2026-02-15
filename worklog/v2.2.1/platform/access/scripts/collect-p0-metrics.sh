#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCESS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RAW_DIR="${ACCESS_DIR}/raw"

HOURS=24
MODE="normal"
ARCH="$(uname -m 2>/dev/null || echo unknown)"
INGESTION_LOG="$(cd "${ACCESS_DIR}/../../../.." && pwd)/logs/dts-ingestion/app.log"
RESULT_OVERRIDE=""
NOTE=""

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --hours <n>           Log scan window in hours (default: 24)
  --mode <name>         legacy/normal/dev (default: normal)
  --arch <name>         architecture label (default: uname -m)
  --ingestion-log <f>   ingestion app log path (default: logs/dts-ingestion/app.log)
  --result <name>       force result PASS/FAIL/OBSERVED
  --note <text>         note written to csv
  --raw-dir <dir>       output directory (default: platform/access/raw)
  -h, --help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --hours)
      HOURS="${2:-}"
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
    --ingestion-log)
      INGESTION_LOG="${2:-}"
      shift 2
      ;;
    --result)
      RESULT_OVERRIDE="${2:-}"
      shift 2
      ;;
    --note)
      NOTE="${2:-}"
      shift 2
      ;;
    --raw-dir)
      RAW_DIR="${2:-}"
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

mkdir -p "${RAW_DIR}"
RUN_AT="$(date -u +"%Y%m%dT%H%M%SZ")"
METRICS_CSV="${RAW_DIR}/p0-access-metrics-${RUN_AT}.csv"
SUMMARY_TXT="${RAW_DIR}/p0-access-summary-${RUN_AT}.txt"

if [[ ! -f "${INGESTION_LOG}" ]]; then
  echo "ingestion log not found: ${INGESTION_LOG}" >&2
  {
    echo "run_at=${RUN_AT}"
    echo "mode=${MODE}"
    echo "arch=${ARCH}"
    echo "hours=${HOURS}"
    echo "ingestion_log=${INGESTION_LOG}"
    echo "status=missing_log"
  } > "${SUMMARY_TXT}"
  {
    echo "run_at,mode,arch,hours,total_trigger,triggered,failed,dag404,tasklog404,result,note,ingestion_log"
    echo "${RUN_AT},${MODE},${ARCH},${HOURS},0,0,0,0,0,OBSERVED,missing_ingestion_log,${INGESTION_LOG}"
  } > "${METRICS_CSV}"
  echo "summary_txt=$(basename "${SUMMARY_TXT}")"
  echo "metrics_csv=$(basename "${METRICS_CSV}")"
  exit 0
fi

WINDOW_START_EPOCH="$(date -u -d "-${HOURS} hours" +%s 2>/dev/null || true)"
if [[ -z "${WINDOW_START_EPOCH}" ]]; then
  WINDOW_START_EPOCH=0
fi

PY_RESULT="$(python3 - <<'PY' "${INGESTION_LOG}" "${WINDOW_START_EPOCH}"
import re
import sys
from datetime import datetime

path = sys.argv[1]
window_start = int(sys.argv[2]) if len(sys.argv) > 2 else 0

iso_head = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+\-]\d{2}:\d{2}))")

def in_window(line: str) -> bool:
    m = iso_head.match(line)
    if not m:
        return True
    try:
        dt = datetime.fromisoformat(m.group(1).replace('Z', '+00:00'))
        return int(dt.timestamp()) >= window_start
    except Exception:
        return True

total_trigger = 0
triggered = 0
failed = 0
dag404 = 0
tasklog404 = 0

with open(path, 'r', encoding='utf-8', errors='ignore') as f:
    for raw in f:
        line = raw.strip()
        if not line:
            continue
        if not in_window(line):
            continue
        if "Airflow trigger result for task" in line:
            total_trigger += 1
            if "status=triggered" in line:
                triggered += 1
            if "status=failed" in line:
                failed += 1
        if "DAG with dag_id" in line and "not found" in line:
            dag404 += 1
        if "TaskInstance not found" in line:
            tasklog404 += 1

print(f"{total_trigger},{triggered},{failed},{dag404},{tasklog404}")
PY
)"

IFS=',' read -r TOTAL_TRIGGER TRIGGERED FAILED DAG404 TASKLOG404 <<< "${PY_RESULT}"

RESULT="OBSERVED"
if [[ "${TOTAL_TRIGGER}" -gt 0 ]]; then
  if [[ "${DAG404}" -eq 0 && "${TASKLOG404}" -eq 0 && "${FAILED}" -eq 0 ]]; then
    RESULT="PASS"
  elif [[ "${DAG404}" -gt 0 || "${TASKLOG404}" -gt 0 ]]; then
    RESULT="FAIL"
  fi
fi
if [[ -n "${RESULT_OVERRIDE}" ]]; then
  RESULT="${RESULT_OVERRIDE}"
fi

{
  echo "run_at,mode,arch,hours,total_trigger,triggered,failed,dag404,tasklog404,result,note,ingestion_log"
  echo "${RUN_AT},${MODE},${ARCH},${HOURS},${TOTAL_TRIGGER},${TRIGGERED},${FAILED},${DAG404},${TASKLOG404},${RESULT},${NOTE:-none},${INGESTION_LOG}"
} > "${METRICS_CSV}"

{
  echo "run_at=${RUN_AT}"
  echo "mode=${MODE}"
  echo "arch=${ARCH}"
  echo "hours=${HOURS}"
  echo "ingestion_log=${INGESTION_LOG}"
  echo "total_trigger=${TOTAL_TRIGGER}"
  echo "triggered=${TRIGGERED}"
  echo "failed=${FAILED}"
  echo "dag404=${DAG404}"
  echo "tasklog404=${TASKLOG404}"
  echo "result=${RESULT}"
  echo "note=${NOTE:-none}"
} > "${SUMMARY_TXT}"

echo "summary_txt=$(basename "${SUMMARY_TXT}")"
echo "metrics_csv=$(basename "${METRICS_CSV}")"

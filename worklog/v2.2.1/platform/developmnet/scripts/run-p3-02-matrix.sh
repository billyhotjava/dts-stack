#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ELT_DIR="$(cd "${DEV_DIR}/../elt" && pwd)"
ELT_RAW_DIR="${ELT_DIR}/raw"
ELT_MATRIX_SCRIPT="${ELT_DIR}/scripts/run-matrix.sh"
RAW_DIR="${DEV_DIR}/raw"
REPORT_SCRIPT="${SCRIPT_DIR}/render-p3-02-report.sh"

HOURS=168
MODES="normal,legacy,dev"
ARCH="$(uname -m 2>/dev/null || echo unknown)"
TZ_NAME="Asia/Shanghai"
RESULT="OBSERVED"
DRY_RUN=0

usage() {
  cat <<'USAGE'
Usage: run-p3-02-matrix.sh [options]

Options:
  --hours <n>         Collection window in hours (default: 168)
  --modes <csv>       Modes list, default: normal,legacy,dev
  --arch <name>       Arch label, default: uname -m
  --tz <zone>         Timezone label, default: Asia/Shanghai
  --result <value>    PASS/FAIL/OBSERVED, default: OBSERVED
  --dry-run           Print command only, do not execute
  -h, --help

Environment:
  Uses the validated matrix runner under:
  worklog/v2.2.1/platform/elt/scripts/run-matrix.sh
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --hours)
      HOURS="${2:-}"
      shift 2
      ;;
    --modes)
      MODES="${2:-}"
      shift 2
      ;;
    --arch)
      ARCH="${2:-}"
      shift 2
      ;;
    --tz)
      TZ_NAME="${2:-}"
      shift 2
      ;;
    --result)
      RESULT="${2:-}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=1
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

if [[ ! -x "${ELT_MATRIX_SCRIPT}" ]]; then
  echo "Matrix runner not found or not executable: ${ELT_MATRIX_SCRIPT}" >&2
  exit 1
fi

mkdir -p "${RAW_DIR}"
before_tmp="$(mktemp)"
after_tmp="$(mktemp)"
new_tmp="$(mktemp)"
trap 'rm -f "${before_tmp}" "${after_tmp}" "${new_tmp}"' EXIT
ls -1 "${ELT_RAW_DIR}"/summary-*.txt 2>/dev/null | sort > "${before_tmp}" || true

cmd=(
  bash "${ELT_MATRIX_SCRIPT}"
  --hours "${HOURS}"
  --modes "${MODES}"
  --arch "${ARCH}"
  --tz "${TZ_NAME}"
  --result "${RESULT}"
  --note-prefix "p3-02-devcenter"
  --tag-prefix "p3-02-devcenter"
  --allow-arch-override
)

echo "Running P3-02 matrix with command:"
printf '  %q' "${cmd[@]}"
echo

if [[ "${DRY_RUN}" -eq 1 ]]; then
  exit 0
fi

"${cmd[@]}"

ls -1 "${ELT_RAW_DIR}"/summary-*.txt 2>/dev/null | sort > "${after_tmp}" || true
comm -13 "${before_tmp}" "${after_tmp}" > "${new_tmp}" || true

if [[ ! -s "${new_tmp}" ]]; then
  latest_summary="$(ls -t "${ELT_RAW_DIR}"/summary-*.txt 2>/dev/null | head -n 1 || true)"
  [[ -n "${latest_summary}" ]] && echo "${latest_summary}" > "${new_tmp}"
fi

copied_count=0
while IFS= read -r summary_file; do
  [[ -f "${summary_file}" ]] || continue
  run_at_utc="$(awk -F= '$1=="run_at_utc"{print $2}' "${summary_file}" | tail -n 1)"
  [[ -n "${run_at_utc}" ]] || run_at_utc="$(date -u +%Y%m%dT%H%M%SZ)"
  hourly_csv_name="$(awk -F= '$1=="hourly_csv"{print $2}' "${summary_file}" | tail -n 1)"
  failure_csv_name="$(awk -F= '$1=="failure_csv"{print $2}' "${summary_file}" | tail -n 1)"

  summary_target="${RAW_DIR}/p3-02-summary-${run_at_utc}.txt"
  cp -f "${summary_file}" "${summary_target}"
  echo "Copied summary -> ${summary_target}"

  if [[ -n "${hourly_csv_name}" && -f "${ELT_RAW_DIR}/${hourly_csv_name}" ]]; then
    hourly_target="${RAW_DIR}/p3-02-hourly-${run_at_utc}.csv"
    cp -f "${ELT_RAW_DIR}/${hourly_csv_name}" "${hourly_target}"
    echo "Copied hourly  -> ${hourly_target}"
  fi

  if [[ -n "${failure_csv_name}" && -f "${ELT_RAW_DIR}/${failure_csv_name}" ]]; then
    failure_target="${RAW_DIR}/p3-02-failure-top-${run_at_utc}.csv"
    cp -f "${ELT_RAW_DIR}/${failure_csv_name}" "${failure_target}"
    echo "Copied failure -> ${failure_target}"
  fi
  copied_count=$((copied_count + 1))
done < "${new_tmp}"

if [[ "${copied_count}" -eq 0 ]]; then
  echo "No summary copied from ${ELT_RAW_DIR}" >&2
  exit 1
fi

if [[ -x "${REPORT_SCRIPT}" ]]; then
  bash "${REPORT_SCRIPT}"
fi

echo "P3-02 matrix run complete."

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCESS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RAW_DIR="${ACCESS_DIR}/raw"

HOURS=24
MODE="normal"
ARCH="$(uname -m 2>/dev/null || echo unknown)"
STRICT=0
INGESTION_LOG="$(cd "${ACCESS_DIR}/../../../.." && pwd)/logs/dts-ingestion/app.log"
NOTE=""

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --hours <n>           log scan window (default: 24)
  --mode <name>         legacy/normal/dev (default: normal)
  --arch <name>         architecture label (default: uname -m)
  --ingestion-log <f>   ingestion app log path
  --note <text>         note written to metrics csv
  --strict              exit non-zero when result != PASS
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
    --note)
      NOTE="${2:-}"
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

mkdir -p "${RAW_DIR}"

COLLECT_OUT="$(bash "${SCRIPT_DIR}/collect-p0-metrics.sh" \
  --hours "${HOURS}" \
  --mode "${MODE}" \
  --arch "${ARCH}" \
  --ingestion-log "${INGESTION_LOG}" \
  --raw-dir "${RAW_DIR}" \
  --note "${NOTE}")"

METRICS_BASENAME="$(echo "${COLLECT_OUT}" | awk -F= '/^metrics_csv=/{print $2}' | tail -n1)"
if [[ -z "${METRICS_BASENAME}" ]]; then
  echo "failed to resolve metrics csv from collect output" >&2
  exit 1
fi
METRICS_CSV="${RAW_DIR}/${METRICS_BASENAME}"

bash "${SCRIPT_DIR}/render-p0-summary.sh" --metrics "${METRICS_CSV}"

RESULT="$(awk -F, 'NR==2 {print $10}' "${METRICS_CSV}")"
REPORT_MD="${ACCESS_DIR}/report/p0-regression-summary.md"

echo "p0 regression completed"
echo "- metrics : ${METRICS_CSV}"
echo "- report  : ${REPORT_MD}"
echo "- result  : ${RESULT}"

if [[ "${STRICT}" -eq 1 && "${RESULT}" != "PASS" ]]; then
  echo "strict mode failed: result=${RESULT}" >&2
  exit 2
fi

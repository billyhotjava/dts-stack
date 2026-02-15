#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCESS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

RUN_BUILD=1
RUN_MATRIX=1
HOURS=24
MATRIX="normal:x86_64,legacy:aarch64,dev:x86_64"
INGESTION_LOG="${ROOT_DIR}/logs/dts-ingestion/app.log"

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --skip-build            skip compile/build checks
  --skip-matrix           skip p0 matrix regression
  --hours <n>             matrix log window in hours (default: 24)
  --matrix <spec>         matrix spec, default: normal:x86_64,legacy:aarch64,dev:x86_64
  --ingestion-log <path>  ingestion log path
  -h, --help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)
      RUN_BUILD=0
      shift
      ;;
    --skip-matrix)
      RUN_MATRIX=0
      shift
      ;;
    --hours)
      HOURS="${2:-}"
      shift 2
      ;;
    --matrix)
      MATRIX="${2:-}"
      shift 2
      ;;
    --ingestion-log)
      INGESTION_LOG="${2:-}"
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

echo "[preflight] root=${ROOT_DIR}"

if [[ "${RUN_BUILD}" -eq 1 ]]; then
  echo "[preflight] compile ingestion"
  (cd "${ROOT_DIR}/source/dts-ingestion" && mvn -DskipTests compile)
  echo "[preflight] compile platform"
  (cd "${ROOT_DIR}/source/dts-platform" && mvn -DskipTests compile)
  echo "[preflight] build platform-webapp"
  (cd "${ROOT_DIR}" && pnpm -C source/dts-platform-webapp build)
fi

if [[ "${RUN_MATRIX}" -eq 1 ]]; then
  echo "[preflight] run p0 matrix"
  bash "${SCRIPT_DIR}/run-p0-matrix.sh" \
    --hours "${HOURS}" \
    --matrix "${MATRIX}" \
    --ingestion-log "${INGESTION_LOG}"
fi

echo "[preflight] done"
echo "[preflight] summary: ${ACCESS_DIR}/report/completion-summary.md"

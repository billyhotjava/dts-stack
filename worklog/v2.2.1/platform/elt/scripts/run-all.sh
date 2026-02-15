#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RAW_DIR="${PLATFORM_DIR}/raw"
V221_DIR="$(cd "${PLATFORM_DIR}/.." && pwd)"

HOURS=24
TZ_NAME="Asia/Shanghai"
MODE="normal"
ARCH="$(uname -m 2>/dev/null || echo unknown)"
RESULT="OBSERVED"
NOTE=""
PACKAGE_TAG=""
REQUIRE_DATA=0
ALLOW_ARCH_OVERRIDE=0

SEM_LEFT=""
SEM_RIGHT=""
SEM_KEY=""
SEM_LEFT_LABEL="ADDAX"
SEM_RIGHT_LABEL="AIRBYTE"

ISO_BEFORE=""
ISO_AFTER=""
ISO_TARGET_PLAN=""

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Core options:
  --hours <n>             Collection window hours (default: 24)
  --tz <zone>             Timezone label (default: Asia/Shanghai)
  --mode <name>           legacy/normal/dev (default: normal)
  --arch <name>           Architecture label (default: uname -m)
  --result <value>        PASS/FAIL/OBSERVED for env matrix (default: OBSERVED)
  --note <text>           Matrix note text
  --tag <name>            Deliverable package tag
  --require-data          Fail if collected task total is 0
  --allow-arch-override   Allow arch label != host arch

Optional semantic compare:
  --sem-left <csv>
  --sem-right <csv>
  --sem-key <column>
  --sem-left-label <name>   (default: ADDAX)
  --sem-right-label <name>  (default: AIRBYTE)

Optional isolation compare:
  --iso-before <csv>
  --iso-after <csv>
  --iso-target-plan <id|name>

  -h, --help
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
    --result)
      RESULT="${2:-}"
      shift 2
      ;;
    --note)
      NOTE="${2:-}"
      shift 2
      ;;
    --tag)
      PACKAGE_TAG="${2:-}"
      shift 2
      ;;
    --require-data)
      REQUIRE_DATA=1
      shift
      ;;
    --allow-arch-override)
      ALLOW_ARCH_OVERRIDE=1
      shift
      ;;
    --sem-left)
      SEM_LEFT="${2:-}"
      shift 2
      ;;
    --sem-right)
      SEM_RIGHT="${2:-}"
      shift 2
      ;;
    --sem-key)
      SEM_KEY="${2:-}"
      shift 2
      ;;
    --sem-left-label)
      SEM_LEFT_LABEL="${2:-}"
      shift 2
      ;;
    --sem-right-label)
      SEM_RIGHT_LABEL="${2:-}"
      shift 2
      ;;
    --iso-before)
      ISO_BEFORE="${2:-}"
      shift 2
      ;;
    --iso-after)
      ISO_AFTER="${2:-}"
      shift 2
      ;;
    --iso-target-plan)
      ISO_TARGET_PLAN="${2:-}"
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

BACKFILL_ARGS=(
  --hours "${HOURS}"
  --mode "${MODE}"
  --arch "${ARCH}"
  --tz "${TZ_NAME}"
)
if [[ "${ALLOW_ARCH_OVERRIDE}" -eq 1 ]]; then
  BACKFILL_ARGS+=(--allow-arch-override)
fi
bash "${SCRIPT_DIR}/backfill-first-run.sh" "${BACKFILL_ARGS[@]}"

bash "${SCRIPT_DIR}/update-env-matrix.sh" \
  --mode "${MODE}" \
  --arch "${ARCH}" \
  --result "${RESULT}" \
  --note "${NOTE}"

if [[ -f "${V221_DIR}/p0-regression-matrix.md" && -f "${V221_DIR}/p0-regression-checklist.md" ]]; then
  bash "${SCRIPT_DIR}/update-p0-regression.sh"
else
  echo "warning: skip update-p0-regression (p0-regression-matrix.md or checklist not found under ${V221_DIR})"
fi

LATEST_SUMMARY="$(ls -t "${RAW_DIR}"/summary-*.txt 2>/dev/null | head -n1 || true)"
LATEST_HOURLY=""
TOTAL_JOBS=0
if [[ -n "${LATEST_SUMMARY}" ]]; then
  LATEST_HOURLY_BASENAME="$(grep -E '^hourly_csv=' "${LATEST_SUMMARY}" | cut -d= -f2- || true)"
  if [[ -n "${LATEST_HOURLY_BASENAME}" ]]; then
    LATEST_HOURLY="${RAW_DIR}/${LATEST_HOURLY_BASENAME}"
  fi
  if [[ -f "${LATEST_HOURLY}" ]]; then
    TOTAL_JOBS="$(awk -F, 'NR>1 && $1!="message" {s+=$2} END {print s+0}' "${LATEST_HOURLY}")"
  fi
fi

if [[ "${REQUIRE_DATA}" -eq 1 && "${TOTAL_JOBS}" -eq 0 ]]; then
  echo "no execution records collected (total=0), abort by --require-data" >&2
  exit 2
fi

if [[ -n "${SEM_LEFT}" || -n "${SEM_RIGHT}" || -n "${SEM_KEY}" ]]; then
  if [[ -z "${SEM_LEFT}" || -z "${SEM_RIGHT}" || -z "${SEM_KEY}" ]]; then
    echo "semantic compare requires --sem-left --sem-right --sem-key together" >&2
    exit 1
  fi
  bash "${SCRIPT_DIR}/semantic-compare.sh" \
    --left "${SEM_LEFT}" \
    --right "${SEM_RIGHT}" \
    --key "${SEM_KEY}" \
    --left-label "${SEM_LEFT_LABEL}" \
    --right-label "${SEM_RIGHT_LABEL}" \
    --append-doc
fi

if [[ -n "${ISO_BEFORE}" || -n "${ISO_AFTER}" ]]; then
  if [[ -z "${ISO_BEFORE}" || -z "${ISO_AFTER}" ]]; then
    echo "isolation compare requires --iso-before and --iso-after together" >&2
    exit 1
  fi
  ISO_CMD=(bash "${SCRIPT_DIR}/isolation-lineage-check.sh" compare --before "${ISO_BEFORE}" --after "${ISO_AFTER}" --append-doc)
  if [[ -n "${ISO_TARGET_PLAN}" ]]; then
    ISO_CMD+=(--target-plan "${ISO_TARGET_PLAN}")
  fi
  "${ISO_CMD[@]}"
fi

if [[ -n "${PACKAGE_TAG}" ]]; then
  bash "${SCRIPT_DIR}/package-report.sh" --tag "${PACKAGE_TAG}"
else
  bash "${SCRIPT_DIR}/package-report.sh"
fi

LATEST_PACKAGE="$(ls -t "${PLATFORM_DIR}"/deliverable-*.tar.gz 2>/dev/null | head -n1 || true)"
echo "pipeline completed"
echo "- latest summary : ${LATEST_SUMMARY:-N/A}"
echo "- latest hourly  : ${LATEST_HOURLY:-N/A}"
echo "- total jobs     : ${TOTAL_JOBS}"
echo "- latest package : ${LATEST_PACKAGE:-N/A}"

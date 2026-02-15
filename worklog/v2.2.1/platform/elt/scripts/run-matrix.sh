#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_ALL_SCRIPT="${SCRIPT_DIR}/run-all.sh"
PLATFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
MATRIX_CSV="${PLATFORM_DIR}/raw/env-matrix.csv"

HOURS=168
TZ_NAME="Asia/Shanghai"
ARCH="$(uname -m 2>/dev/null || echo unknown)"
MODES="normal,legacy,dev"
RESULT="OBSERVED"
NOTE_PREFIX="matrix"
TAG_PREFIX="matrix"
REQUIRE_DATA=0
DRY_RUN=0
ALLOW_ARCH_OVERRIDE=0

SEM_LEFT=""
SEM_RIGHT=""
SEM_KEY=""
SEM_LEFT_LABEL="ADDAX"
SEM_RIGHT_LABEL="AIRBYTE"
ISO_BEFORE=""
ISO_AFTER=""
ISO_TARGET_PLAN=""
WITH_P1=0

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Core options:
  --hours <n>             Collection window (default: 168)
  --tz <zone>             Timezone label (default: Asia/Shanghai)
  --arch <name>           Architecture label (default: uname -m)
  --modes <csv>           Modes list: normal,legacy,dev (default: all three)
  --result <value>        PASS/FAIL/OBSERVED (default: OBSERVED)
  --note-prefix <text>    Note prefix in env matrix (default: matrix)
  --tag-prefix <text>     Deliverable tag prefix (default: matrix)
  --require-data          Require total jobs > 0
  --allow-arch-override  Allow arch label != host arch
  --dry-run               Print commands only

Optional P1 compare on each mode:
  --with-p1               Enable semantic/isolation compare
  --sem-left <csv>
  --sem-right <csv>
  --sem-key <column>
  --sem-left-label <name>    (default: ADDAX)
  --sem-right-label <name>   (default: AIRBYTE)
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
    --arch)
      ARCH="${2:-}"
      shift 2
      ;;
    --modes)
      MODES="${2:-}"
      shift 2
      ;;
    --result)
      RESULT="${2:-}"
      shift 2
      ;;
    --note-prefix)
      NOTE_PREFIX="${2:-}"
      shift 2
      ;;
    --tag-prefix)
      TAG_PREFIX="${2:-}"
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
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --with-p1)
      WITH_P1=1
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

if ! [[ "${HOURS}" =~ ^[0-9]+$ ]] || [[ "${HOURS}" -lt 1 ]]; then
  echo "--hours must be a positive integer" >&2
  exit 1
fi

IFS=',' read -r -a MODE_LIST <<< "${MODES}"
if [[ "${#MODE_LIST[@]}" -eq 0 ]]; then
  echo "--modes is empty" >&2
  exit 1
fi

if [[ "${WITH_P1}" -eq 1 ]]; then
  if [[ -z "${SEM_LEFT}" || -z "${SEM_RIGHT}" || -z "${SEM_KEY}" ]]; then
    echo "--with-p1 requires --sem-left --sem-right --sem-key" >&2
    exit 1
  fi
  if [[ -z "${ISO_BEFORE}" || -z "${ISO_AFTER}" ]]; then
    echo "--with-p1 requires --iso-before --iso-after" >&2
    exit 1
  fi
fi

for mode_raw in "${MODE_LIST[@]}"; do
  mode="$(echo "${mode_raw}" | xargs)"
  if [[ -z "${mode}" ]]; then
    continue
  fi

  note="${NOTE_PREFIX}-${mode}-${ARCH}"
  tag="${TAG_PREFIX}-${mode}-${ARCH}"

  cmd=(
    bash "${RUN_ALL_SCRIPT}"
    --hours "${HOURS}"
    --mode "${mode}"
    --arch "${ARCH}"
    --tz "${TZ_NAME}"
    --result "${RESULT}"
    --note "${note}"
    --tag "${tag}"
  )
  if [[ "${REQUIRE_DATA}" -eq 1 ]]; then
    cmd+=(--require-data)
  fi
  if [[ "${ALLOW_ARCH_OVERRIDE}" -eq 1 ]]; then
    cmd+=(--allow-arch-override)
  fi

  if [[ "${WITH_P1}" -eq 1 ]]; then
    cmd+=(
      --sem-left "${SEM_LEFT}"
      --sem-right "${SEM_RIGHT}"
      --sem-key "${SEM_KEY}"
      --sem-left-label "${SEM_LEFT_LABEL}"
      --sem-right-label "${SEM_RIGHT_LABEL}"
      --iso-before "${ISO_BEFORE}"
      --iso-after "${ISO_AFTER}"
    )
    if [[ -n "${ISO_TARGET_PLAN}" ]]; then
      cmd+=(--iso-target-plan "${ISO_TARGET_PLAN}")
    fi
  fi

  echo "== mode: ${mode} =="
  printf 'cmd:'
  printf ' %q' "${cmd[@]}"
  echo

  if [[ "${DRY_RUN}" -eq 0 ]]; then
    "${cmd[@]}"
  fi
done

if [[ -f "${MATRIX_CSV}" ]]; then
  echo
  echo "latest matrix rows:"
  awk -F, -v arch="${ARCH}" 'NR==1{h=$0; next} $3==arch {print}' "${MATRIX_CSV}" | tail -n 10
fi

echo "matrix run completed"

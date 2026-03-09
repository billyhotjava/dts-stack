#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="${REPORT_DIR:-tests/reports}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
SUMMARY_MD="${ROOT_DIR}/${REPORT_DIR}/${TIMESTAMP}-gates-summary.md"
SUMMARY_JSON="${ROOT_DIR}/${REPORT_DIR}/${TIMESTAMP}-gates-summary.json"

INCLUDE_OPTIONAL=0
FAIL_FAST=0
WITH_WEB_E2E_CORE=0
WITH_WEB_E2E_FULL=0
WITH_WEB_E2E_QUARANTINE=0
WITH_BIZ_E2E=0
DRY_RUN=0
CUSTOM_SUITES=0
GATE_PROFILE=""
REPEAT_COUNT=1
SUITES=("web-e2e-core")

append_suite() {
  local candidate="$1"
  [[ -z "${candidate}" ]] && return 0
  local existing
  for existing in "${SUITES[@]}"; do
    if [[ "${existing}" == "${candidate}" ]]; then
      return 0
    fi
  done
  SUITES+=("${candidate}")
}

apply_gate_profile() {
  local profile="$1"
  SUITES=()
  case "${profile}" in
    pr)
      append_suite "web-e2e-core"
      ;;
    nightly)
      append_suite "web-e2e-full"
      ;;
    release)
      append_suite "web-e2e-full"
      ;;
    *)
      echo "[ERROR] unsupported gate profile: ${profile}" >&2
      echo "[ERROR] supported values: pr, nightly, release" >&2
      exit 2
      ;;
  esac
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --include-optional)
      INCLUDE_OPTIONAL=1
      shift
      ;;
    --fail-fast)
      FAIL_FAST=1
      shift
      ;;
    --gate)
      [[ $# -lt 2 ]] && { echo "[ERROR] --gate requires a value" >&2; exit 2; }
      GATE_PROFILE="$2"
      shift 2
      ;;
    --suite)
      [[ $# -lt 2 ]] && { echo "[ERROR] --suite requires a value" >&2; exit 2; }
      SUITES=("$2")
      CUSTOM_SUITES=1
      shift 2
      ;;
    --suites)
      [[ $# -lt 2 ]] && { echo "[ERROR] --suites requires comma-separated values" >&2; exit 2; }
      IFS=',' read -r -a SUITES <<< "$2"
      CUSTOM_SUITES=1
      shift 2
      ;;
    --repeat)
      [[ $# -lt 2 ]] && { echo "[ERROR] --repeat requires an integer value" >&2; exit 2; }
      REPEAT_COUNT="$2"
      shift 2
      ;;
    --with-web-e2e-core)
      WITH_WEB_E2E_CORE=1
      shift
      ;;
    --without-web-e2e-core)
      WITH_WEB_E2E_CORE=0
      shift
      ;;
    --with-web-e2e-full)
      WITH_WEB_E2E_FULL=1
      shift
      ;;
    --without-web-e2e-full)
      WITH_WEB_E2E_FULL=0
      shift
      ;;
    --with-web-e2e-quarantine)
      WITH_WEB_E2E_QUARANTINE=1
      shift
      ;;
    --without-web-e2e-quarantine)
      WITH_WEB_E2E_QUARANTINE=0
      shift
      ;;
    --with-biz-e2e)
      WITH_BIZ_E2E=1
      shift
      ;;
    --without-biz-e2e)
      WITH_BIZ_E2E=0
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    *)
      echo "[ERROR] unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if ! [[ "${REPEAT_COUNT}" =~ ^[0-9]+$ ]] || [[ "${REPEAT_COUNT}" -lt 1 ]]; then
  echo "[ERROR] --repeat must be a positive integer" >&2
  exit 2
fi

if [[ -n "${GATE_PROFILE}" && "${CUSTOM_SUITES}" -eq 1 ]]; then
  echo "[ERROR] --gate cannot be combined with --suite/--suites" >&2
  exit 2
fi

if [[ -n "${GATE_PROFILE}" ]]; then
  apply_gate_profile "${GATE_PROFILE}"
fi

if [[ "${WITH_WEB_E2E_CORE}" -eq 1 && "${CUSTOM_SUITES}" -eq 0 ]]; then
  append_suite "web-e2e-core"
fi
if [[ "${WITH_WEB_E2E_FULL}" -eq 1 && "${CUSTOM_SUITES}" -eq 0 ]]; then
  append_suite "web-e2e-full"
fi
if [[ "${WITH_WEB_E2E_QUARANTINE}" -eq 1 && "${CUSTOM_SUITES}" -eq 0 ]]; then
  append_suite "web-e2e-quarantine"
fi
if [[ "${WITH_BIZ_E2E}" -eq 1 && "${CUSTOM_SUITES}" -eq 0 ]]; then
  append_suite "biz-e2e"
fi

if [[ "${DRY_RUN}" -eq 1 ]]; then
  echo "[DRY-RUN] gate=${GATE_PROFILE:-custom} repeat=${REPEAT_COUNT} suites to run (${#SUITES[@]}):"
  for SUITE in "${SUITES[@]}"; do
    echo "- ${SUITE}"
  done
  exit 0
fi

mkdir -p "${ROOT_DIR}/${REPORT_DIR}"

echo "# DTS Web Gate Summary (${TIMESTAMP})" > "${SUMMARY_MD}"
echo "" >> "${SUMMARY_MD}"
echo "| Suite | Exit Code | Status |" >> "${SUMMARY_MD}"
echo "|---|---:|---|" >> "${SUMMARY_MD}"

SUMMARY_ITEMS=()
FAILED=0
STOP_ALL=0
for ((RUN_INDEX=1; RUN_INDEX<=REPEAT_COUNT; RUN_INDEX++)); do
  for SUITE in "${SUITES[@]}"; do
    SUITE="$(echo "${SUITE}" | xargs)"
    [[ -z "${SUITE}" ]] && continue

    CMD=(python3 tests/run_suite.py --suite "${SUITE}" --report-dir "${REPORT_DIR}")
    if [[ "${INCLUDE_OPTIONAL}" -eq 1 ]]; then
      CMD+=(--include-optional)
    fi
    if [[ "${FAIL_FAST}" -eq 1 ]]; then
      CMD+=(--fail-fast)
    fi

    echo "[INFO] running gate suite=${SUITE} run=${RUN_INDEX}/${REPEAT_COUNT}"
    set +e
    (cd "${ROOT_DIR}" && "${CMD[@]}")
    RC=$?
    set -e

    STATUS="PASS"
    if [[ "${RC}" -ne 0 ]]; then
      STATUS="FAIL"
      FAILED=1
    fi

    echo "| ${SUITE}#${RUN_INDEX} | ${RC} | ${STATUS} |" >> "${SUMMARY_MD}"
    SUMMARY_ITEMS+=("{\"suite\":\"${SUITE}\",\"runIndex\":${RUN_INDEX},\"exitCode\":${RC},\"status\":\"${STATUS}\"}")

    if [[ "${RC}" -ne 0 && "${FAIL_FAST}" -eq 1 ]]; then
      echo "[INFO] fail-fast enabled, stop after suite=${SUITE} run=${RUN_INDEX}" >&2
      STOP_ALL=1
      break
    fi
  done
  if [[ "${STOP_ALL}" -eq 1 ]]; then
    break
  fi
done

{
  echo "{"
  echo "  \"timestamp\": \"${TIMESTAMP}\","
  echo "  \"failed\": ${FAILED},"
  echo "  \"suites\": ["
  for i in "${!SUMMARY_ITEMS[@]}"; do
    if [[ "${i}" -gt 0 ]]; then
      echo "    ,${SUMMARY_ITEMS[$i]}"
    else
      echo "    ${SUMMARY_ITEMS[$i]}"
    fi
  done
  echo "  ]"
  echo "}"
} > "${SUMMARY_JSON}"

echo "[INFO] summary md  : ${SUMMARY_MD}"
echo "[INFO] summary json: ${SUMMARY_JSON}"

if [[ "${FAILED}" -ne 0 ]]; then
  echo "[FAIL] gate suites have failures" >&2
  exit 1
fi

echo "[PASS] all gate suites passed"

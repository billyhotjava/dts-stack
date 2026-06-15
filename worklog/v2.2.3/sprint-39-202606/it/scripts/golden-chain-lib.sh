#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPRINT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
EVIDENCE_DIR="${EVIDENCE_DIR:-${SPRINT_DIR}/evidence}"
PLATFORM_BASE_URL="${PLATFORM_BASE_URL:-http://127.0.0.1:18082}"

print_plan() {
  local scenario="$1"
  shift
  echo "scenario=${scenario}"
  echo "mode=dry-run"
  echo "set RUN_LIVE=1 to collect live evidence"
  echo "platform_base_url=${PLATFORM_BASE_URL}"
  echo "checks:"
  for check in "$@"; do
    echo "- ${check}"
  done
}

collect_chain_evidence() {
  local scenario="$1"
  local chain_key="$2"
  local evidence_file="${EVIDENCE_DIR}/golden-chain-${scenario}-$(date +%Y%m%d%H%M%S).json"
  local curl_auth_args=()

  if [[ -n "${DTS_BEARER_TOKEN:-}" ]]; then
    curl_auth_args=(-H "Authorization: Bearer ${DTS_BEARER_TOKEN}")
  elif [[ -n "${DTS_PORTAL_SESSION_TOKEN:-}" ]]; then
    curl_auth_args=(-H "Cookie: portal_session=${DTS_PORTAL_SESSION_TOKEN}")
  fi

  mkdir -p "${EVIDENCE_DIR}"
  {
    echo "{"
    echo "  \"scenario\": \"${scenario}\","
    echo "  \"chainKey\": \"${chain_key}\","
    echo "  \"capturedAt\": \"$(date -Iseconds)\","
    echo "  \"list\": "
    curl -fsS "${curl_auth_args[@]}" "${PLATFORM_BASE_URL}/api/golden-chains"
    echo ","
    echo "  \"detail\": "
    curl -fsS "${curl_auth_args[@]}" "${PLATFORM_BASE_URL}/api/golden-chains/${chain_key}"
    echo
    echo "}"
  } >"${evidence_file}"

  echo "evidence_file=${evidence_file}"
}

run_or_plan() {
  local scenario="$1"
  local chain_key="$2"
  shift 2
  if [[ "${RUN_LIVE:-0}" != "1" ]]; then
    print_plan "${scenario}" "$@"
    exit 0
  fi
  collect_chain_evidence "${scenario}" "${chain_key}"
}

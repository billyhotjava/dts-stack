#!/usr/bin/env bash
# =============================================================================
# dts-common.sh — Shared function library for DTS CLI tools
#
# Usage:
#   SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#   source "${SCRIPT_DIR}/lib/dts-common.sh"
#
# Optional overrides (set BEFORE sourcing):
#   CALLER_NAME   — prefix for info/warn/die messages (auto-detected if unset)
# =============================================================================

# Guard against double-sourcing
[[ -n "${_DTS_COMMON_LOADED:-}" ]] && return 0
_DTS_COMMON_LOADED=1

# ---------------------------------------------------------------------------
# CALLER_NAME — auto-detect from calling script, allow override
# ---------------------------------------------------------------------------
if [[ -z "${CALLER_NAME:-}" ]]; then
  # BASH_SOURCE[1] is the script that sourced us; fall back to [0]
  CALLER_NAME="$(basename "${BASH_SOURCE[1]:-${BASH_SOURCE[0]:-dts}}")"
  # Strip .sh extension if present
  CALLER_NAME="${CALLER_NAME%.sh}"
fi

# ---------------------------------------------------------------------------
# Color output
# ---------------------------------------------------------------------------
red()    { printf '\033[0;31m%s\033[0m\n' "$*"; }
green()  { printf '\033[0;32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }
info()   { echo "[$CALLER_NAME] $*"; }
warn()   { yellow "[$CALLER_NAME] WARNING: $*"; }
die()    { red "[$CALLER_NAME] ERROR: $*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# Environment validation
#   require_env "VAR1" "VAR2" ...
#   Dies if any named variable is unset or empty.
# ---------------------------------------------------------------------------
require_env() {
  local missing=()
  for var in "$@"; do
    if [[ -z "${!var:-}" ]]; then
      missing+=("$var")
    fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    die "required environment variable(s) not set: ${missing[*]}"
  fi
}

# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

# Global flag — set to "-k" to skip TLS verification
_DTS_INSECURE_FLAG="${_DTS_INSECURE_FLAG:-}"

# Internal variables populated by dts_init_http()
_DTS_API_URL=""
_DTS_AUTH_HEADER=""
_DTS_DEPT_HEADER=""

# dts_init_http
#   Reads API_BASE, TOKEN, ACTIVE_DEPT from env and sets up internal state.
#   Must be called before dts_curl_get / dts_curl_post_json.
dts_init_http() {
  require_env API_BASE TOKEN

  _DTS_API_URL="${API_BASE%/}/api"
  _DTS_AUTH_HEADER="Authorization: Bearer $TOKEN"
  _DTS_DEPT_HEADER=""
  if [[ -n "${ACTIVE_DEPT:-}" ]]; then
    _DTS_DEPT_HEADER="X-Active-Dept: $ACTIVE_DEPT"
  fi

  # Export API_URL for callers that reference it directly
  API_URL="$_DTS_API_URL"
  AUTH_HEADER="$_DTS_AUTH_HEADER"
}

# dts_curl_get <url>
dts_curl_get() {
  local url="$1"
  local args=(-sS -H "$_DTS_AUTH_HEADER" $_DTS_INSECURE_FLAG)
  if [[ -n "$_DTS_DEPT_HEADER" ]]; then
    args+=(-H "$_DTS_DEPT_HEADER")
  fi
  curl "${args[@]}" "$url"
}

# dts_curl_post_json <url> <json_data>
dts_curl_post_json() {
  local url="$1"
  local data="$2"
  local args=(-sS -X POST -H "$_DTS_AUTH_HEADER" -H "Content-Type: application/json" $_DTS_INSECURE_FLAG)
  if [[ -n "$_DTS_DEPT_HEADER" ]]; then
    args+=(-H "$_DTS_DEPT_HEADER")
  fi
  curl "${args[@]}" -d "$data" "$url"
}

# ---------------------------------------------------------------------------
# JSON extraction
#   json_extract <file> <field>
#   Uses jq if available, falls back to python3.
# ---------------------------------------------------------------------------
json_extract() {
  local file="$1"
  local field="$2"

  if command -v jq >/dev/null 2>&1; then
    jq -r ".$field // empty" "$file"
  else
    python3 -c "
import sys, json
data = json.load(open(sys.argv[1]))
val = data
for key in sys.argv[2].split('.'):
    if isinstance(val, dict):
        val = val.get(key)
    else:
        val = None
        break
if val is not None:
    print(val)
" "$file" "$field" 2>/dev/null || true
  fi
}

# ---------------------------------------------------------------------------
# URL encoding
#   url_encode <string>
# ---------------------------------------------------------------------------
url_encode() {
  python3 -c "import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))" "$1"
}

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

usage() {
  echo "Usage: $0 [app|dev|legacy] [docker compose down options]" >&2
}

normalize_mode() {
  case "${1:-}" in
    app|single|images|"") echo "app" ;;
    dev|local) echo "dev" ;;
    legacy) echo "legacy" ;;
    *) return 1 ;;
  esac
}

mode_from_env() {
  local legacy_stack=""
  local deploy_mode=""
  if [[ -f ./.env ]]; then
    legacy_stack="$(grep -E '^LEGACY_STACK=' ./.env | head -n1 | cut -d= -f2- | tr -d '\r' || true)"
    deploy_mode="$(grep -E '^DEPLOY_MODE=' ./.env | head -n1 | cut -d= -f2- | tr -d '\r' || true)"
  fi
  if [[ "${legacy_stack}" == "true" ]]; then
    echo "legacy"
  else
    normalize_mode "${deploy_mode:-app}"
  fi
}

mode=""
if [[ $# -gt 0 ]]; then
  if [[ "$1" == "-h" || "$1" == "--help" ]]; then
    usage
    exit 0
  elif mode="$(normalize_mode "$1" 2>/dev/null)"; then
    shift
  else
    mode="$(mode_from_env)"
  fi
else
  mode="$(mode_from_env)"
fi

case "$mode" in
  app) compose_file="docker-compose-app.yml" ;;
  dev) compose_file="docker-compose.dev.yml" ;;
  legacy) compose_file="docker-compose.legacy.yml" ;;
  *) usage; exit 1 ;;
esac

export DTS_DBT_HOST_PROJECT_DIR="${DTS_DBT_HOST_PROJECT_DIR:-${SCRIPT_DIR}/services/dts-dbt}"
export STACK_ROOT="${STACK_ROOT:-${SCRIPT_DIR}}"

if docker compose version >/dev/null 2>&1; then
  compose_cmd=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose_cmd=(docker-compose)
else
  echo "[stop.sh] docker compose not found." >&2
  exit 1
fi

extra_args=("$@")
if [[ ${#extra_args[@]} -eq 0 ]]; then
  extra_args=(--remove-orphans)
fi

echo "[stop.sh] Using ${compose_file} (mode: ${mode})."
"${compose_cmd[@]}" -f "${compose_file}" down "${extra_args[@]}"

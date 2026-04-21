#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ $# -gt 0 && "$1" =~ ^(single|ha2|cluster|legacy)$ ]]; then
  MODE="$1"
  shift
else
  MODE=""
fi

if [[ -z "${MODE}" && -f ./.env ]]; then
  MODE="$(grep -E '^DEPLOY_MODE=' ./.env | head -n1 | cut -d= -f2- | tr -d '\r')"
fi

if [[ -z "${MODE}" ]]; then
  MODE="single"
fi

case "$MODE" in
  single)
    COMPOSE_FILE="docker-compose.yml"
    ;;
  ha2)
    COMPOSE_FILE="docker-compose.ha2.yml"
    ;;
  cluster)
    COMPOSE_FILE="docker-compose.cluster.yml"
    ;;
  legacy)
    COMPOSE_FILE="docker-compose.legacy.yml"
    ;;
  *)
    echo "[stop.sh] Unknown mode '${MODE}'." >&2
    echo "Usage: $0 [single|ha2|cluster|legacy] [docker compose down options]" >&2
    exit 1
    ;;
esac

# 即使是 stop/down，docker compose 仍要 resolve compose 文件里的变量，
# 避免 "variable is not set" 警告；与 start.sh / dev-up.sh 保持一致。
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

echo "[stop.sh] Using ${COMPOSE_FILE} (mode: ${MODE})."
"${compose_cmd[@]}" -f "${COMPOSE_FILE}" down "${extra_args[@]}"

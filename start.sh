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
    echo "[start.sh] Unknown mode '${MODE}'." >&2
    echo "Usage: $0 [single|ha2|cluster|legacy] [service ...]" >&2
    exit 1
    ;;
esac

# 让 DbtScopedProjectService 把容器视角 scoped 路径翻译为主机视角，
# 供 Airflow 用作 `docker -v HOST:CONTAINER` 的 HOST 部分。
# 优先：调用方显式 export → .env 已有 → 按 SCRIPT_DIR 推导。
# 与 dev-up.sh 保持一致，保证 dev/app/legacy 三种入口都不会因为旧 .env 漏键而导致
# dbt 报 "No dbt_project.yml found"。
export DTS_DBT_HOST_PROJECT_DIR="${DTS_DBT_HOST_PROJECT_DIR:-${SCRIPT_DIR}/services/dts-dbt}"
export STACK_ROOT="${STACK_ROOT:-${SCRIPT_DIR}}"

if docker compose version >/dev/null 2>&1; then
  compose_cmd=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose_cmd=(docker-compose)
else
  echo "[start.sh] docker compose not found." >&2
  exit 1
fi

echo "[start.sh] Using ${COMPOSE_FILE} (mode: ${MODE})."
"${compose_cmd[@]}" -f "${COMPOSE_FILE}" up -d "$@"

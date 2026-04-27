#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$REPO_ROOT"

export DTS_DBT_HOST_PROJECT_DIR="${DTS_DBT_HOST_PROJECT_DIR:-${REPO_ROOT}/services/dts-dbt}"
export STACK_ROOT="${STACK_ROOT:-${REPO_ROOT}}"

failures=0

check_file() {
  if [ -f "$1" ]; then
    echo "OK file: $1"
  else
    echo "MISS file: $1"
    failures=$((failures + 1))
  fi
}

check_dir() {
  if [ -d "$1" ]; then
    echo "OK dir: $1"
  else
    echo "MISS dir: $1"
    failures=$((failures + 1))
  fi
}

echo "DTS release preflight"
check_file docker-compose-app.yml
check_file docker-compose.dev.yml
check_file docker-compose.legacy.yml
check_file imgversion.conf
check_file builds/dts-build.sh
check_file bin/dts-upgrade-lite
check_file docs/release/v2.2.3/upgrade-lite-operations-kylin-kunpeng.md
check_file docs/release/v2.2.3/offline-upgrade-checklist-kylin-kunpeng.md
check_file docs/release/v2.2.3/offline-upgrade-guide-kylin-kunpeng.md
check_dir builds
check_dir services
check_dir services/dts-dbt
check_file services/dts-dbt/dbt_project.yml
check_file services/dts-keycloak/realm-dts.json
check_dir services/certs

echo
echo "Tool availability"
for tool in docker git; do
  if command -v "$tool" >/dev/null 2>&1; then
    echo "OK tool: $tool"
  else
    echo "MISS tool: $tool"
    failures=$((failures + 1))
  fi
done

if command -v docker >/dev/null 2>&1; then
  echo
  echo "Compose config check"
  docker compose -f docker-compose-app.yml config >/dev/null || failures=$((failures + 1))
  docker compose -f docker-compose.dev.yml config >/dev/null || failures=$((failures + 1))
  docker compose -f docker-compose.legacy.yml config >/dev/null || failures=$((failures + 1))
fi

echo
if [ "$failures" -eq 0 ]; then
  echo "Preflight passed"
else
  echo "Preflight failed with $failures issue(s)"
  exit 1
fi

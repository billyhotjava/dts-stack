#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$REPO_ROOT"

section() {
  printf '\n== %s ==\n' "$1"
}

section "Repository"
pwd
git status --short || true

section "Environment Files"
[ -f .env ] && echo ".env present" || echo ".env missing"
[ -f docker-compose.yml ] && echo "docker-compose.yml present" || echo "docker-compose.yml missing"
[ -f docker-compose-app.yml ] && echo "docker-compose-app.yml present" || echo "docker-compose-app.yml missing"

section "Docker"
if command -v docker >/dev/null 2>&1; then
  docker --version || true
  docker compose -f docker-compose.yml -f docker-compose-app.yml ps || true
else
  echo "docker command not found"
fi

section "Writable Runtime Paths"
for path in services/dts-dbt services/dts-dbt/target logs reports; do
  if [ -e "$path" ]; then
    [ -w "$path" ] && echo "writable: $path" || echo "not writable: $path"
  else
    echo "missing: $path"
  fi
done

section "Local Ports"
if command -v ss >/dev/null 2>&1; then
  ss -ltnp 2>/dev/null | grep -E ':(80|443|5432|8080|18080|18081|18082)\b' || true
else
  echo "ss command not found"
fi

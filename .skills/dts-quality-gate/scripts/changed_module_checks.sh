#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$REPO_ROOT"

RUN=0
if [ "${1:-}" = "--run" ]; then
  RUN=1
elif [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  echo "Usage: $0 [--run]"
  exit 0
elif [ "${1:-}" != "" ]; then
  echo "Unknown argument: $1" >&2
  exit 2
fi

declare -A seen_files=()
files=()

add_file() {
  local file="$1"
  [ -n "$file" ] || return
  [ -n "${seen_files[$file]:-}" ] && return
  seen_files["$file"]=1
  files+=("$file")
}

while IFS= read -r file; do
  add_file "$file"
done < <(git diff --name-only HEAD 2>/dev/null || git diff --name-only 2>/dev/null || true)

while IFS= read -r file; do
  add_file "$file"
done < <(git status --short | awk '{print $NF}')

declare -a commands=()

add_cmd() {
  local cmd="$1"
  for existing in "${commands[@]:-}"; do
    [ "$existing" = "$cmd" ] && return
  done
  commands+=("$cmd")
}

for file in "${files[@]}"; do
  case "$file" in
    .skills/*)
      add_cmd ".skills/dts-quality-gate/scripts/validate_skills.py"
      add_cmd "find .skills -path '*/scripts/*.sh' -type f -print0 | xargs -0 -n1 bash -n"
      ;;
    source/dts-admin/*) add_cmd "cd source/dts-admin && npm run backend:unit:test" ;;
    source/dts-platform/*) add_cmd "cd source/dts-platform && npm run backend:unit:test" ;;
    source/dts-common/*) add_cmd "cd source/dts-common && npm run backend:unit:test" ;;
    source/dts-admin-webapp/*) add_cmd "cd source/dts-admin-webapp && pnpm build" ;;
    source/dts-platform-webapp/*) add_cmd "cd source/dts-platform-webapp && pnpm build" ;;
    source/dts-analytics-webapp/modern/*)
      add_cmd "cd source/dts-analytics-webapp/modern && pnpm typecheck"
      add_cmd "cd source/dts-analytics-webapp/modern && pnpm build"
      ;;
    services/dts-dbt/*) add_cmd ".skills/dts-dbt-modeling-governance/scripts/dbt_guard.sh" ;;
    docker-compose*.yml|builds/*|services/*)
      add_cmd "docker compose -f docker-compose-app.yml config"
      add_cmd "docker compose -f docker-compose.dev.yml config"
      add_cmd "docker compose -f docker-compose.legacy.yml config"
      ;;
    tests/api-e2e-java/*) add_cmd "cd tests/api-e2e-java && mvn test" ;;
    tests/web-e2e/*) add_cmd "cd tests/web-e2e && pnpm test" ;;
  esac
done

if [ "${#commands[@]}" -eq 0 ]; then
  echo "No module-specific checks inferred from current changes."
  exit 0
fi

echo "Suggested checks:"
for cmd in "${commands[@]}"; do
  echo "  $cmd"
done

if [ "$RUN" -eq 1 ]; then
  for cmd in "${commands[@]}"; do
    echo
    echo "Running: $cmd"
    bash -lc "$cmd"
  done
fi

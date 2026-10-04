#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
DBT_PROJECT="${DBT_PROJECT:-$REPO_ROOT/services/dts-dbt}"
RUN_PARSE=0

for arg in "$@"; do
  case "$arg" in
    --parse) RUN_PARSE=1 ;;
    -h|--help)
      echo "Usage: $0 [--parse]"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

warn() {
  echo "WARN: $*" >&2
}

info() {
  echo "INFO: $*"
}

[ -d "$DBT_PROJECT" ] || fail "dbt project not found: $DBT_PROJECT"
[ -f "$DBT_PROJECT/dbt_project.yml" ] || fail "missing dbt_project.yml in $DBT_PROJECT"
[ -d "$DBT_PROJECT/models" ] || fail "missing models directory in $DBT_PROJECT"

info "Checking dbt project at $DBT_PROJECT"

bad_names=0
while IFS= read -r -d '' file; do
  base="$(basename "$file" .sql)"
  if [[ ! "$base" =~ ^[A-Za-z][A-Za-z0-9_]*$ ]]; then
    echo "BAD_NAME: $file"
    bad_names=$((bad_names + 1))
  fi
done < <(find "$DBT_PROJECT/models" -type f -name '*.sql' -print0)

[ "$bad_names" -eq 0 ] || fail "$bad_names model file(s) have invalid dbt/platform names"

if find "$DBT_PROJECT/models" -path '*/custom/*' -type f -name '*.sql' | grep -q .; then
  custom_bad=0
  while IFS= read -r file; do
    base="$(basename "$file" .sql)"
    if [[ ! "$base" =~ ^biz_(dwd|dws|ads)_ ]]; then
      echo "CUSTOM_PREFIX_WARN: $file"
      custom_bad=$((custom_bad + 1))
    fi
  done < <(find "$DBT_PROJECT/models" -path '*/custom/*' -type f -name '*.sql')
  [ "$custom_bad" -eq 0 ] || warn "$custom_bad custom model(s) do not use biz_dwd_/biz_dws_/biz_ads_ prefixes"
fi

if [ "$RUN_PARSE" -eq 1 ]; then
  command -v dbt >/dev/null 2>&1 || fail "dbt command not found"
  info "Running dbt parse"
  (cd "$DBT_PROJECT" && dbt parse)
else
  info "Static checks passed. Add --parse to run dbt parse."
fi

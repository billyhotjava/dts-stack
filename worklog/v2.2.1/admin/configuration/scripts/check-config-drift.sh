#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/.env}"
OUT_DIR="${2:-$ROOT_DIR/worklog/v2.2.1/admin/configuration/raw}"
OUT_FILE="$OUT_DIR/config-drift-report.tsv"

PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-dts_admin}"
PGUSER="${PGUSER:-dts_admin}"
PGPASSWORD="${PGPASSWORD:-}"

mkdir -p "$OUT_DIR"

if [[ -z "$PGPASSWORD" ]]; then
  echo "PGPASSWORD is required" >&2
  exit 1
fi

declare -A env_map=()
while IFS='=' read -r key value; do
  [[ -z "$key" ]] && continue
  [[ "$key" =~ ^# ]] && continue
  [[ ! "$key" =~ ^[A-Z0-9_]+$ ]] && continue
  env_map["$key"]="$value"
done <"$ENV_FILE"

SQL=$'select cfg_key, coalesce(cfg_value, \'\') as cfg_value, coalesce(config_scope, \'RUNTIME\') as scope\nfrom system_config\norder by cfg_key;'

export PGPASSWORD
rows="$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -At -F $'\t' -c "$SQL")"

printf "cfg_key\tscope\tenv_value\tdb_value\tstatus\n" >"$OUT_FILE"
while IFS=$'\t' read -r cfg_key db_value scope; do
  [[ -z "$cfg_key" ]] && continue
  env_value="${env_map[$cfg_key]-}"
  status="db_only"
  if [[ -n "${env_map[$cfg_key]+x}" ]]; then
    if [[ "$env_value" == "$db_value" ]]; then
      status="same"
    else
      status="drift"
    fi
  fi
  printf "%s\t%s\t%s\t%s\t%s\n" "$cfg_key" "$scope" "$env_value" "$db_value" "$status" >>"$OUT_FILE"
done <<<"$rows"

echo "generated: $OUT_FILE"

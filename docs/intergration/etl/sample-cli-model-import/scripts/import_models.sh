#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST_PATH="${1:-${ROOT_DIR}/manifest/models.tsv}"

API_BASE="${API_BASE:-}"
TOKEN="${TOKEN:-}"
PLAN_ID="${PLAN_ID:-}"
SOURCE_DATA_SOURCE_ID="${SOURCE_DATA_SOURCE_ID:-}"
ACTIVE_DEPT="${ACTIVE_DEPT:-}"

if [[ -z "${API_BASE}" || -z "${TOKEN}" || -z "${PLAN_ID}" || -z "${SOURCE_DATA_SOURCE_ID}" ]]; then
  echo "[ERROR] missing required env vars: API_BASE TOKEN PLAN_ID SOURCE_DATA_SOURCE_ID" >&2
  exit 1
fi

if [[ ! -f "${MANIFEST_PATH}" ]]; then
  echo "[ERROR] manifest not found: ${MANIFEST_PATH}" >&2
  exit 1
fi

append_form_if_present() {
  local key="$1"
  local val="$2"
  if [[ -n "${val}" ]]; then
    CURL_ARGS+=( -F "${key}=${val}" )
  fi
}

echo "[INFO] start import, manifest=${MANIFEST_PATH}"

line_no=0
while IFS=$'\t' read -r name layer sql_path source_ds_id alias schema_name materialized tags status enabled owner_dept description csv_path; do
  line_no=$((line_no + 1))

  if [[ ${line_no} -eq 1 && "${name}" == "name" ]]; then
    continue
  fi
  if [[ -z "${name}" || "${name:0:1}" == "#" ]]; then
    continue
  fi

  sql_abs="${ROOT_DIR}/${sql_path}"
  if [[ ! -f "${sql_abs}" ]]; then
    echo "[ERROR] line ${line_no}: sql file not found: ${sql_abs}" >&2
    exit 1
  fi

  ds_id="${source_ds_id}"
  if [[ -z "${ds_id}" ]]; then
    ds_id="${SOURCE_DATA_SOURCE_ID}"
  fi

  CURL_ARGS=(
    -fsS
    -X POST
    "${API_BASE%/}/api/modeling/sql-models/import"
    -H "Authorization: Bearer ${TOKEN}"
    -F "planId=${PLAN_ID}"
    -F "name=${name}"
    -F "layer=${layer}"
    -F "sourceDataSourceId=${ds_id}"
    -F "sql=@${sql_abs};type=text/plain"
  )

  if [[ -n "${ACTIVE_DEPT}" ]]; then
    CURL_ARGS+=( -H "X-Active-Dept: ${ACTIVE_DEPT}" )
  fi

  append_form_if_present "alias" "${alias}"
  append_form_if_present "schemaName" "${schema_name}"
  append_form_if_present "materialized" "${materialized}"
  append_form_if_present "tags" "${tags}"
  append_form_if_present "description" "${description}"
  append_form_if_present "status" "${status}"
  append_form_if_present "enabled" "${enabled}"
  append_form_if_present "ownerDept" "${owner_dept}"

  if [[ -n "${csv_path}" ]]; then
    csv_abs="${ROOT_DIR}/${csv_path}"
    if [[ ! -f "${csv_abs}" ]]; then
      echo "[ERROR] line ${line_no}: csv file not found: ${csv_abs}" >&2
      exit 1
    fi
    CURL_ARGS+=( -F "csv=@${csv_abs};type=text/csv" )
  fi

  echo "[INFO] importing: ${name} (${layer})"
  curl "${CURL_ARGS[@]}" >/dev/null
  echo "[OK] imported: ${name}"
done < "${MANIFEST_PATH}"

echo "[DONE] import completed"

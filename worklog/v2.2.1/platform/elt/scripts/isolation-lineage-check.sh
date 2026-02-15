#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${PLATFORM_DIR}/../../.." && pwd)"
RAW_DIR="${PLATFORM_DIR}/raw"
TARGET_DOC="${PLATFORM_DIR}/isolation-lineage-regression.md"

ACTION="${1:-}"
if [[ -n "${ACTION}" ]]; then
  shift
fi

SNAPSHOT_FILE=""
LABEL=""
BEFORE_FILE=""
AFTER_FILE=""
TARGET_PLAN=""
APPEND_DOC=0

usage() {
  cat <<USAGE
Usage:
  $(basename "$0") snapshot [options]
  $(basename "$0") compare --before <csv> --after <csv> [options]

Commands:
  snapshot
    Capture current platform isolation/lineage snapshot from Postgres.
  compare
    Compare two snapshot csv files and output crosstalk conclusion.

Snapshot options:
  --label <name>      Label used in output filename
  --out <file>        Output csv path (default: raw/isolation-snapshot-<ts>.csv)

Compare options:
  --before <file>     Before snapshot csv
  --after <file>      After snapshot csv
  --target-plan <id|name>
                      Expected changed plan (for crosstalk judgement)
  --append-doc        Append summary to isolation-lineage-regression.md

General:
  -h, --help          Show help
USAGE
}

if [[ -z "${ACTION}" || "${ACTION}" == "-h" || "${ACTION}" == "--help" ]]; then
  usage
  exit 0
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --label)
      LABEL="${2:-}"
      shift 2
      ;;
    --out)
      SNAPSHOT_FILE="${2:-}"
      shift 2
      ;;
    --before)
      BEFORE_FILE="${2:-}"
      shift 2
      ;;
    --after)
      AFTER_FILE="${2:-}"
      shift 2
      ;;
    --target-plan)
      TARGET_PLAN="${2:-}"
      shift 2
      ;;
    --append-doc)
      APPEND_DOC=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

mkdir -p "${RAW_DIR}"

ENV_FILE="${REPO_DIR}/.env"
PG_DB="dts_platform"
PG_USER="dts_platform"
PG_PWD=""
if [[ -f "${ENV_FILE}" ]]; then
  PG_DB="$(grep -E '^PG_DB_DTPS=' "${ENV_FILE}" | head -n1 | cut -d= -f2- || true)"
  PG_USER="$(grep -E '^PG_USER_DTPS=' "${ENV_FILE}" | head -n1 | cut -d= -f2- || true)"
  PG_PWD="$(grep -E '^PG_PWD_DTPS=' "${ENV_FILE}" | head -n1 | cut -d= -f2- || true)"
  [[ -z "${PG_DB}" ]] && PG_DB="dts_platform"
  [[ -z "${PG_USER}" ]] && PG_USER="dts_platform"
fi

find_pg_container() {
  docker ps --format '{{.Names}}' 2>/dev/null | grep -E '(^|[-_])(dts-pg|postgres)([-_]|$)' | head -n1 || true
}

PG_CONTAINER="$(find_pg_container)"

run_psql_csv() {
  local sql="$1"
  docker exec -e PGPASSWORD="${PG_PWD}" "${PG_CONTAINER}" \
    psql -U "${PG_USER}" -d "${PG_DB}" -v ON_ERROR_STOP=1 -c "COPY (${sql}) TO STDOUT WITH CSV HEADER"
}

table_exists() {
  local table_name="$1"
  docker exec -e PGPASSWORD="${PG_PWD}" "${PG_CONTAINER}" \
    psql -U "${PG_USER}" -d "${PG_DB}" -At -c "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='${table_name}') THEN 1 ELSE 0 END"
}

snapshot_action() {
  if [[ -z "${PG_CONTAINER}" ]]; then
    echo "postgres container not found" >&2
    exit 1
  fi

  local run_at
  run_at="$(date -u +"%Y%m%dT%H%M%SZ")"
  local suffix="${run_at}"
  if [[ -n "${LABEL}" ]]; then
    suffix="${suffix}-${LABEL}"
  fi
  if [[ -z "${SNAPSHOT_FILE}" ]]; then
    SNAPSHOT_FILE="${RAW_DIR}/isolation-snapshot-${suffix}.csv"
  fi

  {
    echo "entity_type,entity_key,entity_name,row_count,signature"

    if [[ "$(table_exists "modeling_plan")" == "1" && "$(table_exists "modeling_sql_model")" == "1" ]]; then
      run_psql_csv "
      SELECT
        'plan' AS entity_type,
        p.id::text AS entity_key,
        p.name AS entity_name,
        COUNT(m.id)::bigint AS row_count,
        COALESCE(
          md5(string_agg(
            COALESCE(m.id::text,'') || '|' || COALESCE(m.name,'') || '|' || COALESCE(m.layer,'') || '|' || COALESCE(m.model_path,''),
            ',' ORDER BY m.name, m.id
          )),
          md5('')
        ) AS signature
      FROM modeling_plan p
      LEFT JOIN modeling_sql_model m ON m.plan_id = p.id
      GROUP BY p.id, p.name
      ORDER BY p.name, p.id
      " | tail -n +2
    fi

    if [[ "$(table_exists "query_dataset_asset")" == "1" ]]; then
      run_psql_csv "
      SELECT
        'dataset' AS entity_type,
        'ALL' AS entity_key,
        'query_dataset_asset' AS entity_name,
        COUNT(*)::bigint AS row_count,
        COALESCE(
          md5(string_agg(
            COALESCE(id::text,'') || '|' || COALESCE(name,'') || '|' || COALESCE(status,'') || '|' || COALESCE(owner_dept,''),
            ',' ORDER BY name, id
          )),
          md5('')
        ) AS signature
      FROM query_dataset_asset
      " | tail -n +2
    fi

    if [[ "$(table_exists "infra_ods_table_mapping")" == "1" ]]; then
      run_psql_csv "
      SELECT
        'ods_mapping' AS entity_type,
        'ALL' AS entity_key,
        'infra_ods_table_mapping' AS entity_name,
        COUNT(*)::bigint AS row_count,
        COALESCE(
          md5(string_agg(
            COALESCE(id::text,'') || '|' || COALESCE(system_code,'') || '|' || COALESCE(ods_schema,'') || '|' || COALESCE(ods_table,''),
            ',' ORDER BY system_code, ods_schema, ods_table, id
          )),
          md5('')
        ) AS signature
      FROM infra_ods_table_mapping
      " | tail -n +2
    fi

    if [[ "$(table_exists "bi_report_link")" == "1" ]]; then
      run_psql_csv "
      SELECT
        'bi_link' AS entity_type,
        'ALL' AS entity_key,
        'bi_report_link' AS entity_name,
        COUNT(*)::bigint AS row_count,
        COALESCE(
          md5(string_agg(
            COALESCE(id::text,'') || '|' || COALESCE(code,'') || '|' || COALESCE(title,'') || '|' || COALESCE(classification,''),
            ',' ORDER BY code, id
          )),
          md5('')
        ) AS signature
      FROM bi_report_link
      " | tail -n +2
    fi
  } > "${SNAPSHOT_FILE}"

  echo "snapshot completed"
  echo "- file: ${SNAPSHOT_FILE}"
}

compare_action() {
  if [[ -z "${BEFORE_FILE}" || -z "${AFTER_FILE}" ]]; then
    echo "compare requires --before and --after" >&2
    exit 1
  fi
  if [[ ! -f "${BEFORE_FILE}" || ! -f "${AFTER_FILE}" ]]; then
    echo "before/after snapshot file not found" >&2
    exit 1
  fi

  local run_at report_file tmp_dir
  run_at="$(date -u +"%Y%m%dT%H%M%SZ")"
  report_file="${RAW_DIR}/isolation-lineage-compare-${run_at}.md"
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "${tmp_dir:-}"' EXIT

  awk -F, 'NR>1 {k=$1"@"$2; print k"|"$1"|"$2"|"$3"|"$4"|"$5}' "${BEFORE_FILE}" | sort > "${tmp_dir}/before.norm"
  awk -F, 'NR>1 {k=$1"@"$2; print k"|"$1"|"$2"|"$3"|"$4"|"$5}' "${AFTER_FILE}" | sort > "${tmp_dir}/after.norm"

  cut -d'|' -f1 "${tmp_dir}/before.norm" > "${tmp_dir}/before.keys"
  cut -d'|' -f1 "${tmp_dir}/after.norm" > "${tmp_dir}/after.keys"

  comm -23 "${tmp_dir}/before.keys" "${tmp_dir}/after.keys" | awk -F"@" '{print $1"|"$2}' > "${tmp_dir}/removed.keys"
  comm -13 "${tmp_dir}/before.keys" "${tmp_dir}/after.keys" | awk -F"@" '{print $1"|"$2}' > "${tmp_dir}/added.keys"
  join -t '|' -1 1 -2 1 <(sort "${tmp_dir}/before.norm") <(sort "${tmp_dir}/after.norm") | awk -F'|' '$4!=$9 || $5!=$10 || $6!=$11 {print $2"|"$3}' > "${tmp_dir}/changed.keys"

  cat "${tmp_dir}/added.keys" "${tmp_dir}/removed.keys" "${tmp_dir}/changed.keys" | sort -u > "${tmp_dir}/impacted.keys"

  IMPACTED_TOTAL="$(wc -l < "${tmp_dir}/impacted.keys" | tr -d ' ')"

  TARGET_MATCH=0
  if [[ -n "${TARGET_PLAN}" ]]; then
    awk -F'|' -v target="${TARGET_PLAN}" '
      {
        if ($1=="plan" && ($2==target || tolower($2)==tolower(target))) { hit=1 }
      }
      END { print hit+0 }
    ' "${tmp_dir}/impacted.keys" > "${tmp_dir}/target-hit"

    awk -F'|' -v target="${TARGET_PLAN}" '
      {
        if ($1=="plan" && ($2==target || tolower($2)==tolower(target))) {
          # expected
        } else if ($1=="plan") {
          extra=1
        }
      }
      END { print extra+0 }
    ' "${tmp_dir}/impacted.keys" > "${tmp_dir}/extra-plan-hit"

    TARGET_MATCH="$(cat "${tmp_dir}/target-hit")"
    EXTRA_PLAN_HIT="$(cat "${tmp_dir}/extra-plan-hit")"
  else
    EXTRA_PLAN_HIT=0
  fi

  RESULT="PASS"
  CONCLUSION="无串扰"
  if [[ -n "${TARGET_PLAN}" ]]; then
    if [[ "${TARGET_MATCH}" -eq 0 ]]; then
      RESULT="WARN"
      CONCLUSION="目标项目未检测到变化（请确认操作是否生效）"
    elif [[ "${EXTRA_PLAN_HIT}" -gt 0 ]]; then
      RESULT="FAIL"
      CONCLUSION="检测到跨项目串扰"
    fi
  else
    if [[ "${IMPACTED_TOTAL}" -gt 0 ]]; then
      RESULT="OBSERVED"
      CONCLUSION="检测到变更（未指定目标项目，无法判定串扰）"
    fi
  fi

  {
    echo "# Isolation/Lineage Compare ${run_at}"
    echo
    echo "- before: \`${BEFORE_FILE}\`"
    echo "- after: \`${AFTER_FILE}\`"
    if [[ -n "${TARGET_PLAN}" ]]; then
      echo "- target plan: \`${TARGET_PLAN}\`"
    fi
    echo "- result: **${RESULT}**"
    echo "- conclusion: **${CONCLUSION}**"
    echo
    echo "## Impact Summary"
    echo "- impacted entities: ${IMPACTED_TOTAL}"
    echo "- added keys: $(wc -l < "${tmp_dir}/added.keys" | tr -d ' ')"
    echo "- removed keys: $(wc -l < "${tmp_dir}/removed.keys" | tr -d ' ')"
    echo "- changed keys: $(wc -l < "${tmp_dir}/changed.keys" | tr -d ' ')"
    echo
    echo "## Impacted Keys (top 50)"
    head -n 50 "${tmp_dir}/impacted.keys" | sed 's/^/- /'
  } > "${report_file}"

  if [[ "${APPEND_DOC}" -eq 1 ]]; then
    {
      echo
      echo "## 自动回归 ${run_at}"
      echo "- 输入：\`${BEFORE_FILE}\` -> \`${AFTER_FILE}\`"
      if [[ -n "${TARGET_PLAN}" ]]; then
        echo "- 目标项目：\`${TARGET_PLAN}\`"
      fi
      echo "- 结果：${RESULT}（${CONCLUSION}，impacted=${IMPACTED_TOTAL}）"
      echo "- 报告：\`raw/$(basename "${report_file}")\`"
    } >> "${TARGET_DOC}"
  fi

  echo "compare completed"
  echo "- result: ${RESULT}"
  echo "- report: ${report_file}"
}

case "${ACTION}" in
  snapshot)
    snapshot_action
    ;;
  compare)
    compare_action
    ;;
  *)
    echo "Unknown action: ${ACTION}" >&2
    usage
    exit 1
    ;;
esac

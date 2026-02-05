#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SQL_FILE:-${SCRIPT_DIR}/99-build-all.sql}"

PG_HOST="${PG_HOST:-127.0.0.1}"
PG_PORT="${PG_PORT:-5432}"
PG_DB="${PG_DB:-biadmin}"
PG_USER="${PG_USER:-biadmin}"
PG_PASSWORD="${PG_PASSWORD:-}"
FORCE_DOCKER=0
REPORT_YEAR="${REPORT_YEAR:-}"
REPORT_YEARS="${REPORT_YEARS:-}"
ODS_TABLE="${ODS_TABLE:-}"

for arg in "$@"; do
  case "$arg" in
    --force-docker)
      FORCE_DOCKER=1
      ;;
    *)
      ;;
  esac
done

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "SQL file not found: ${SQL_FILE}" >&2
  exit 1
fi

if [[ -z "${PG_PASSWORD}" ]]; then
  echo "PG_PASSWORD is required. Example:"
  echo "  PG_PASSWORD='Devops123@' ./worklog/s10/patent/run-build-all.sh"
  echo "Optional: set REPORT_YEAR=2024 to build ADS for specific year"
  echo "Optional: set ODS_TABLE=ods_patent_info_202602 to target a custom ODS table"
  exit 1
fi

run_psql() {
  PGPASSWORD="${PG_PASSWORD}" psql \
    -h "${PG_HOST}" \
    -p "${PG_PORT}" \
    -U "${PG_USER}" \
    -d "${PG_DB}" \
    ${REPORT_YEAR:+-v report_year="${REPORT_YEAR}"} \
    ${REPORT_YEARS:+-v report_years="${REPORT_YEARS}"} \
    ${ODS_TABLE:+-v ods_table="${ODS_TABLE}"} \
    -v ON_ERROR_STOP=1 \
    -f "${SQL_FILE}"
}

run_docker_psql() {
  docker run --rm --network host \
    -e PGPASSWORD="${PG_PASSWORD}" \
    postgres:17.6 \
    psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" \
    ${REPORT_YEAR:+-v report_year="${REPORT_YEAR}"} \
    ${REPORT_YEARS:+-v report_years="${REPORT_YEARS}"} \
    ${ODS_TABLE:+-v ods_table="${ODS_TABLE}"} \
    -v ON_ERROR_STOP=1 -f "${SQL_FILE}"
}

if [[ "${FORCE_DOCKER}" -eq 0 ]] && command -v psql >/dev/null 2>&1; then
  echo "[run] using local psql"
  run_psql
  exit 0
fi

if command -v docker >/dev/null 2>&1; then
  echo "[run] local psql not found, trying docker psql"
  run_docker_psql
  exit 0
fi

echo "Neither psql nor docker is available. Please install psql or docker." >&2
exit 1

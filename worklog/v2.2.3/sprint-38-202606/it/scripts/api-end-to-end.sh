#!/usr/bin/env bash
set -euo pipefail

if [[ "${RUN_LIVE:-0}" != "1" ]]; then
  echo "Set RUN_LIVE=1 to run against the local v223 stack." >&2
  exit 2
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
MOCK_NAME="s38-api-mock-e2e"
MOCK_IMAGE="${MOCK_IMAGE:-python:3.11-alpine}"
NETWORK="${DTS_DOCKER_NETWORK:-dts-core}"
PG_CONTAINER="${DTS_PG_CONTAINER:-v223-dts-pg-1}"
INGESTION_CONTAINER="${DTS_INGESTION_CONTAINER:-v223-dts-ingestion-1}"
AIRFLOW_CONTAINER="${DTS_AIRFLOW_CONTAINER:-dts-airflow-scheduler}"
PLATFORM_DB="${DTS_PLATFORM_DB:-dts_platform}"
PLATFORM_USER="${DTS_PLATFORM_USER:-dts_platform}"
TARGET_DB="${DTS_TARGET_DB:-biadmin}"
TARGET_USER="${DTS_TARGET_USER:-biadmin}"
TARGET_PASSWORD="${DTS_TARGET_PASSWORD:?Set DTS_TARGET_PASSWORD for the target database}"
SCHEMA_NAME="s38_api_it"
TARGET_TABLE="${SCHEMA_NAME}.ods_api_orders"
AIRFLOW_DAG_ID="dts_api_e2e_orders"
AIRFLOW_TASK_ID="addax_s38_api_it_ods_api_orders"
SOURCE_ID="00000000-0038-0000-0000-000000000101"
TASK_NAME="s38-api-e2e-orders"
TASK_ID=""

cleanup() {
  docker rm -f "${MOCK_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

psql_platform() {
  docker exec -i "${PG_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${PLATFORM_USER}" -d "${PLATFORM_DB}" "$@"
}

psql_target() {
  docker exec -i "${PG_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${TARGET_USER}" -d "${TARGET_DB}" "$@"
}

json_field() {
  python3 -c 'import json,sys; print(json.load(sys.stdin).get(sys.argv[1], ""))' "$1"
}

start_mock() {
  cleanup
  docker run -d --rm --name "${MOCK_NAME}" --network "${NETWORK}" \
    -v "${ROOT_DIR}/worklog/v2.2.3/sprint-38-202606/it/scripts/api-e2e-mock.py:/mock.py:ro" \
    "${MOCK_IMAGE}" python /mock.py >/dev/null
  for _ in $(seq 1 30); do
    if docker exec "${INGESTION_CONTAINER}" curl -fsS "http://${MOCK_NAME}:18080/health" >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done
  echo "mock API did not become reachable from ${INGESTION_CONTAINER}" >&2
  docker logs "${MOCK_NAME}" >&2 || true
  exit 1
}

seed_data() {
  psql_target <<SQL
DROP TABLE IF EXISTS ${TARGET_TABLE};
DROP SCHEMA IF EXISTS ${SCHEMA_NAME} CASCADE;
CREATE SCHEMA ${SCHEMA_NAME};
SQL

  psql_platform <<SQL
DELETE FROM ingestion_execution WHERE task_id IN (SELECT id FROM ingestion_task WHERE name = '${TASK_NAME}');
DELETE FROM ingestion_task WHERE name = '${TASK_NAME}';
DELETE FROM infra_data_source WHERE id = '${SOURCE_ID}';
INSERT INTO infra_data_source (
  id, name, type, props, status, created_by, created_date, last_modified_by, last_modified_date
) VALUES (
  '${SOURCE_ID}',
  'Sprint-38 API E2E Mock',
  'api',
  '{
    "connectorType": "api",
    "readerType": "httpreader",
    "sourceCategory": "api",
    "contractVersion": "1.2.0",
    "baseUrl": "http://${MOCK_NAME}:18080",
    "auth": {"provider": "none"},
    "requestPolicy": {"allowHttp": true, "allowedHosts": ["${MOCK_NAME}"]},
    "defaultHeaders": {"X-DTS-IT": "sprint-38"}
  }',
  'ACTIVE',
  'codex-it',
  now(),
  'codex-it',
  now()
);
INSERT INTO ingestion_task (
  name, description, source_type, source_data_source_id, source_config,
  destination_type, destination_config, sync_mode, sync_schedule, sync_config, table_mapping,
  airflow_enabled, airflow_dag_id, status, created_by, created_date, last_modified_by, last_modified_date
) VALUES (
  '${TASK_NAME}',
  'Sprint-38 API end-to-end IT seed',
  'api',
  '${SOURCE_ID}',
  '{
    "sourceSystem": "CRM",
    "requestPolicy": {"allowHttp": true, "allowedHosts": ["${MOCK_NAME}"]},
    "resource": {
      "resourceId": "orders",
      "path": "/v1/orders",
      "method": "GET",
      "recordPath": "$.data.items",
      "cursor": {
        "type": "datetime",
        "field": "updatedAt",
        "injectInto": "query",
        "parameterName": "updatedAfter",
        "initialValue": "2026-06-12T00:00:00Z"
      },
      "targetTable": "${TARGET_TABLE}",
      "landing": {"mode": "raw_record", "rawRecordColumn": "_dts_raw_record"}
    }
  }',
  'postgres',
  '{
    "jdbcUrl": "jdbc:postgresql://dts-pg:5432/${TARGET_DB}",
    "username": "${TARGET_USER}",
    "password": "${TARGET_PASSWORD}",
    "driverClass": "org.postgresql.Driver"
  }',
  'incremental',
  null,
  '{"incrementalColumn": "updatedAt", "incrementalType": "datetime", "initialWatermark": "2026-06-12T00:00:00Z"}',
  '[{"source":"orders","target":"${TARGET_TABLE}"}]',
  true,
  'dts_api_e2e_orders',
  'active',
  'codex-it',
  now(),
  'codex-it',
  now()
);
SQL

  TASK_ID="$(psql_platform -At -c "SELECT id FROM ingestion_task WHERE name = '${TASK_NAME}'")"
  if [[ -z "${TASK_ID}" ]]; then
    echo "failed to seed ingestion task" >&2
    exit 1
  fi
}

start_execution() {
  local batch_id="$1"
  docker exec "${INGESTION_CONTAINER}" sh -lc \
    "curl -fsS -H 'Content-Type: application/json' -H 'X-DTS-Service: dts-platform' -X POST --data '{\"taskId\":${TASK_ID},\"batchId\":\"${batch_id}\",\"mode\":\"MANUAL\"}' http://127.0.0.1:8083/internal/api-ingestion/executions"
}

get_execution() {
  local execution_id="$1"
  docker exec "${INGESTION_CONTAINER}" sh -lc \
    "curl -fsS -H 'X-DTS-Service: dts-platform' http://127.0.0.1:8083/internal/api-ingestion/executions/${execution_id}"
}

wait_execution() {
  local execution_id="$1"
  local status=""
  local body=""
  for _ in $(seq 1 60); do
    body="$(get_execution "${execution_id}")"
    status="$(printf '%s' "${body}" | json_field status)"
    if [[ "${status}" == "success" || "${status}" == "failed" ]]; then
      printf '%s' "${body}"
      return
    fi
    sleep 2
  done
  echo "execution ${execution_id} did not finish; last status=${status}" >&2
  exit 1
}

assert_counts() {
  local expected_rows="$1"
  local expected_checkpoint="$2"
  local rows checkpoint
  rows="$(psql_target -At -c "SELECT count(*) FROM ${TARGET_TABLE}")"
  checkpoint="$(psql_target -At -c "SELECT cursor_value FROM dts_api_ingestion_checkpoint WHERE task_id = ${TASK_ID} AND resource_id = 'orders'")"
  if [[ "${rows}" != "${expected_rows}" ]]; then
    echo "expected ${expected_rows} raw rows, got ${rows}" >&2
    exit 1
  fi
  if [[ "${checkpoint}" != "${expected_checkpoint}" ]]; then
    echo "expected checkpoint ${expected_checkpoint}, got ${checkpoint}" >&2
    exit 1
  fi
  echo "raw_rows=${rows}"
  echo "checkpoint=${checkpoint}"
}

trigger_airflow_execution() {
  local run_id="s38-api-e2e-airflow-${TASK_ID}-$(date +%s)"
  local batch_id="s38-e2e-airflow-batch"
  local dag_file="/opt/airflow/dags/${AIRFLOW_DAG_ID}.py"

  docker exec "${AIRFLOW_CONTAINER}" sh -lc "grep -q 'INGESTION_TASK_ID = ${TASK_ID}' '${dag_file}'"
  docker exec "${AIRFLOW_CONTAINER}" airflow dags list | grep "${AIRFLOW_DAG_ID}" >/dev/null
  docker exec "${AIRFLOW_CONTAINER}" airflow dags unpause "${AIRFLOW_DAG_ID}" >/dev/null
  docker exec "${AIRFLOW_CONTAINER}" airflow dags trigger \
    -r "${run_id}" \
    -c "{\"batchId\":\"${batch_id}\",\"mode\":\"MANUAL\"}" \
    "${AIRFLOW_DAG_ID}" >/dev/null
  echo "airflow_run_id=${run_id}"
  wait_airflow_task "${run_id}"
  assert_airflow_execution "${batch_id}"
}

wait_airflow_task() {
  local run_id="$1"
  local state=""
  for _ in $(seq 1 90); do
    state="$(
      docker exec "${AIRFLOW_CONTAINER}" airflow tasks state "${AIRFLOW_DAG_ID}" "${AIRFLOW_TASK_ID}" "${run_id}" 2>/dev/null \
        | tail -n 1 \
        | tr -d '[:space:]'
    )"
    if [[ "${state}" == "success" ]]; then
      echo "airflow_task_state=${state}"
      return
    fi
    if [[ "${state}" == "failed" || "${state}" == "upstream_failed" ]]; then
      echo "airflow task ${AIRFLOW_TASK_ID} failed for run ${run_id}" >&2
      docker exec "${AIRFLOW_CONTAINER}" airflow tasks state "${AIRFLOW_DAG_ID}" "${AIRFLOW_TASK_ID}" "${run_id}" >&2 || true
      exit 1
    fi
    sleep 2
  done
  echo "airflow task ${AIRFLOW_TASK_ID} did not finish; last state=${state}" >&2
  exit 1
}

assert_airflow_execution() {
  local batch_id="$1"
  local row status rows_read rows_written
  row="$(psql_platform -At -F '|' -c "SELECT id, status, rows_read, rows_written FROM ingestion_execution WHERE task_id = ${TASK_ID} AND batch_id = '${batch_id}' ORDER BY id DESC LIMIT 1")"
  IFS='|' read -r execution_id status rows_read rows_written <<<"${row}"
  if [[ -z "${execution_id:-}" ]]; then
    echo "expected Airflow-created ingestion_execution for batch ${batch_id}" >&2
    exit 1
  fi
  if [[ "${status}" != "success" || "${rows_read}" != "1" || "${rows_written}" != "1" ]]; then
    echo "expected Airflow execution success with 1 row, got id=${execution_id} status=${status} rows_read=${rows_read} rows_written=${rows_written}" >&2
    exit 1
  fi
  echo "airflow_execution_id=${execution_id}"
  echo "airflow_execution_status=${status}"
  echo "airflow_rows_read=${rows_read}"
  echo "airflow_rows_written=${rows_written}"
}

main() {
  start_mock
  seed_data

  echo "task_id=${TASK_ID}"
  first_body="$(start_execution "s38-e2e-batch-1")"
  first_id="$(printf '%s' "${first_body}" | json_field id)"
  echo "first_execution_id=${first_id}"
  first_done="$(wait_execution "${first_id}")"
  echo "first_status=$(printf '%s' "${first_done}" | json_field status)"
  echo "first_rows_read=$(printf '%s' "${first_done}" | json_field rowsRead)"
  echo "first_rows_written=$(printf '%s' "${first_done}" | json_field rowsWritten)"
  assert_counts 2 "2026-06-12T00:02:00Z"

  docker exec "${INGESTION_CONTAINER}" curl -fsS "http://${MOCK_NAME}:18080/__append" >/dev/null

  second_body="$(start_execution "s38-e2e-batch-2")"
  second_id="$(printf '%s' "${second_body}" | json_field id)"
  echo "second_execution_id=${second_id}"
  second_done="$(wait_execution "${second_id}")"
  echo "second_status=$(printf '%s' "${second_done}" | json_field status)"
  echo "second_rows_read=$(printf '%s' "${second_done}" | json_field rowsRead)"
  echo "second_rows_written=$(printf '%s' "${second_done}" | json_field rowsWritten)"
  assert_counts 3 "2026-06-12T00:03:00Z"

  docker exec "${INGESTION_CONTAINER}" curl -fsS "http://${MOCK_NAME}:18080/__append" >/dev/null
  trigger_airflow_execution
  assert_counts 4 "2026-06-12T00:04:00Z"

  echo "landing_table=${TARGET_TABLE}"
  echo "mock_api_container=${MOCK_NAME}"
}

main "$@"

#!/usr/bin/env bash
set -euo pipefail

if [[ "${RUN_LIVE:-0}" != "1" ]]; then
  echo "Set RUN_LIVE=1 to rebuild live API DAGs and verify checkpoint continuity."
  exit 0
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
MOCK_NAME="${DTS_IT08_MOCK_NAME:-s38-api-mock-e2e}"
MOCK_IMAGE="${MOCK_IMAGE:-python:3.11-alpine}"
NETWORK="${DTS_DOCKER_NETWORK:-dts-core}"
PG_CONTAINER="${DTS_PG_CONTAINER:-v223-dts-pg-1}"
INGESTION_CONTAINER="${DTS_INGESTION_CONTAINER:-v223-dts-ingestion-1}"
AIRFLOW_CONTAINER="${DTS_AIRFLOW_CONTAINER:-dts-airflow-scheduler}"
PLATFORM_DB="${DTS_PLATFORM_DB:-dts_platform}"
TARGET_DB="${DTS_TARGET_DB:-biadmin}"
PG_USER="${DTS_PG_USER:-postgres}"
TASK_ID="${DTS_IT08_TASK_ID:-90}"
RESOURCE_ID="${DTS_IT08_RESOURCE_ID:-orders}"
TARGET_TABLE="${DTS_IT08_TARGET_TABLE:-s38_api_it.ods_api_orders}"
AIRFLOW_DAG_ID="${DTS_IT08_AIRFLOW_DAG_ID:-dts_api_e2e_orders}"
AIRFLOW_TASK_ID="${DTS_IT08_AIRFLOW_TASK_ID:-addax_s38_api_it_ods_api_orders}"
DAG_FILE="${DTS_IT08_DAG_FILE:-services/dts-airflow/dags/${AIRFLOW_DAG_ID}.py}"

for bin in docker jq grep sed; do
  command -v "$bin" >/dev/null 2>&1 || {
    echo "missing required command: $bin" >&2
    exit 2
  }
done

cleanup() {
  docker rm -f "$MOCK_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

psql_platform_scalar() {
  docker exec -i "$PG_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d "$PLATFORM_DB" -At -F '|' -c "$1"
}

psql_target_scalar() {
  docker exec -i "$PG_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d "$TARGET_DB" -At -F '|' -c "$1"
}

start_mock() {
  cleanup
  docker run -d --rm --name "$MOCK_NAME" --network "$NETWORK" \
    -v "${ROOT_DIR}/worklog/v2.2.3/sprint-38-202606/it/scripts/api-e2e-mock.py:/mock.py:ro" \
    "$MOCK_IMAGE" python /mock.py >/dev/null
  for _ in $(seq 1 30); do
    if docker exec "$INGESTION_CONTAINER" curl -fsS "http://${MOCK_NAME}:18080/health" >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done
  echo "mock API did not become reachable from ${INGESTION_CONTAINER}" >&2
  docker logs "$MOCK_NAME" >&2 || true
  exit 3
}

rebuild_api_dags() {
  docker exec "$INGESTION_CONTAINER" sh -lc \
    "curl -fsS -H 'X-DTS-Service: dts-platform' -X POST http://127.0.0.1:8083/api/ingestion/tasks/dags/rebuild-api"
}

assert_dag_is_thin() {
  local old_markers=(
    "SOURCE_CONFIG_JSON"
    "DTS_API_BEARER_TOKEN"
    "DTS_API_KEY"
    "DTS_API_USERNAME"
    "DTS_API_PASSWORD"
    "psycopg2"
    "_ensure_landing_table"
    "dts_api_ingestion_checkpoint"
    "_request_json"
  )
  [[ -f "$DAG_FILE" ]] || {
    echo "dag_file_missing=${DAG_FILE}" >&2
    exit 4
  }
  grep -Fq "/internal/api-ingestion/executions" "$DAG_FILE"
  grep -Fq "urllib.request.ProxyHandler({})" "$DAG_FILE"
  for marker in "${old_markers[@]}"; do
    if grep -Fq "$marker" "$DAG_FILE"; then
      echo "dag_old_marker_present=${marker}" >&2
      exit 4
    fi
  done
  echo "dag_file=${DAG_FILE}"
  echo "dag_old_markers_present=false"
}

checkpoint_minute() {
  local checkpoint="$1"
  local minute
  minute="$(sed -E 's/.*T[0-9]{2}:([0-9]{2}):[0-9]{2}Z.*/\1/' <<<"$checkpoint")"
  if [[ ! "$minute" =~ ^[0-9]{2}$ ]]; then
    echo "unsupported_checkpoint=${checkpoint}" >&2
    exit 5
  fi
  printf '%d' "$((10#$minute))"
}

append_one_new_record_after_checkpoint() {
  local checkpoint="$1"
  local minute next_minute append_count
  minute="$(checkpoint_minute "$checkpoint")"
  next_minute=$((minute + 1))
  append_count=$((next_minute - 2))
  if (( append_count < 1 )); then
    append_count=1
  fi
  for _ in $(seq 1 "$append_count"); do
    docker exec "$INGESTION_CONTAINER" curl -fsS "http://${MOCK_NAME}:18080/__append" >/dev/null
  done
  printf '2026-06-12T00:%02d:00Z' "$next_minute"
}

trigger_airflow() {
  local run_id="$1"
  local batch_id="$2"
  docker exec "$AIRFLOW_CONTAINER" sh -lc "grep -q 'INGESTION_TASK_ID = ${TASK_ID}' '/opt/airflow/dags/${AIRFLOW_DAG_ID}.py'"
  docker exec "$AIRFLOW_CONTAINER" airflow dags unpause "$AIRFLOW_DAG_ID" >/dev/null 2>/dev/null
  docker exec "$AIRFLOW_CONTAINER" airflow dags trigger \
    -r "$run_id" \
    -c "{\"batchId\":\"${batch_id}\",\"mode\":\"MANUAL\"}" \
    "$AIRFLOW_DAG_ID" >/dev/null 2>/dev/null
}

wait_airflow_task() {
  local run_id="$1"
  local state=""
  for _ in $(seq 1 90); do
    state="$(
      docker exec "$AIRFLOW_CONTAINER" airflow tasks state "$AIRFLOW_DAG_ID" "$AIRFLOW_TASK_ID" "$run_id" 2>/dev/null \
        | tail -n 1 \
        | tr -d '[:space:]'
    )"
    if [[ "$state" == "success" ]]; then
      echo "airflow_task_state=${state}"
      return
    fi
    if [[ "$state" == "failed" || "$state" == "upstream_failed" ]]; then
      echo "airflow_task_failed=${state}" >&2
      exit 6
    fi
    sleep 2
  done
  echo "airflow_task_timeout=${state}" >&2
  exit 6
}

assert_execution_and_checkpoint() {
  local batch_id="$1"
  local before_rows="$2"
  local before_checkpoint="$3"
  local expected_checkpoint="$4"
  local row execution_id status rows_read rows_written after_rows after_checkpoint
  row="$(
    psql_platform_scalar "
      select id, coalesce(status, ''), coalesce(rows_read, 0)::text, coalesce(rows_written, 0)::text
      from ingestion_execution
      where task_id = ${TASK_ID}
        and batch_id = '${batch_id}'
      order by id desc
      limit 1;
    "
  )"
  IFS='|' read -r execution_id status rows_read rows_written <<<"$row"
  after_rows="$(psql_target_scalar "select count(*)::text from ${TARGET_TABLE};")"
  after_checkpoint="$(
    psql_target_scalar "
      select cursor_value
      from dts_api_ingestion_checkpoint
      where task_id = ${TASK_ID}
        and resource_id = '${RESOURCE_ID}';
    "
  )"
  echo "migration_execution_id=${execution_id}"
  echo "migration_execution_status=${status}"
  echo "migration_rows_read=${rows_read}"
  echo "migration_rows_written=${rows_written}"
  echo "rows_before=${before_rows}"
  echo "rows_after=${after_rows}"
  echo "checkpoint_before=${before_checkpoint}"
  echo "checkpoint_after=${after_checkpoint}"
  echo "checkpoint_expected=${expected_checkpoint}"
  [[ "$status" == "success" ]]
  [[ "$rows_read" == "1" ]]
  [[ "$rows_written" == "1" ]]
  [[ "$after_rows" == "$((before_rows + 1))" ]]
  [[ "$after_checkpoint" == "$expected_checkpoint" ]]
}

main() {
  local task_row before_checkpoint before_rows rebuild_body migrated failed run_id batch_id expected_checkpoint
  task_row="$(
    psql_platform_scalar "
      select id, coalesce(source_type, ''), coalesce(airflow_dag_id, ''), coalesce(status, '')
      from ingestion_task
      where id = ${TASK_ID};
    "
  )"
  if [[ -z "$task_row" ]]; then
    echo "api_task_missing=${TASK_ID}" >&2
    exit 7
  fi
  echo "api_task=${task_row}"

  before_checkpoint="$(
    psql_target_scalar "
      select cursor_value
      from dts_api_ingestion_checkpoint
      where task_id = ${TASK_ID}
        and resource_id = '${RESOURCE_ID}';
    "
  )"
  if [[ -z "$before_checkpoint" ]]; then
    echo "checkpoint_missing=true" >&2
    exit 7
  fi
  before_rows="$(psql_target_scalar "select count(*)::text from ${TARGET_TABLE};")"

  start_mock
  rebuild_body="$(rebuild_api_dags)"
  migrated="$(jq -r '.migrated // empty' <<<"$rebuild_body")"
  failed="$(jq -r '.failed // empty' <<<"$rebuild_body")"
  echo "rebuild_total=$(jq -r '.total // empty' <<<"$rebuild_body")"
  echo "rebuild_migrated=${migrated}"
  echo "rebuild_failed=${failed}"
  jq -r --argjson taskId "$TASK_ID" '.items[]? | select(.taskId == $taskId) | "rebuild_item_action=\(.action)\nrebuild_item_dagId=\(.dagId // "")"' <<<"$rebuild_body"
  [[ "$failed" == "0" ]]
  [[ "${migrated:-0}" -ge 1 ]]

  assert_dag_is_thin
  expected_checkpoint="$(append_one_new_record_after_checkpoint "$before_checkpoint")"

  run_id="s38-it08-migration-${TASK_ID}-$(date +%s)"
  batch_id="s38-it08-migration-${TASK_ID}-$(date +%s)"
  echo "airflow_run_id=${run_id}"
  trigger_airflow "$run_id" "$batch_id"
  wait_airflow_task "$run_id"
  assert_execution_and_checkpoint "$batch_id" "$before_rows" "$before_checkpoint" "$expected_checkpoint"
}

main "$@"

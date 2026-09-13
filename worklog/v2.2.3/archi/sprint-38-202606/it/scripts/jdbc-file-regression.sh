#!/usr/bin/env bash
set -euo pipefail

if [[ "${RUN_LIVE:-0}" != "1" ]]; then
  echo "Set RUN_LIVE=1 to run live JDBC and file ingestion regressions against the local v223 stack."
  exit 0
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
PLATFORM_CONTAINER="${DTS_PLATFORM_CONTAINER:-v223-dts-platform-1}"
PG_CONTAINER="${DTS_PG_CONTAINER:-v223-dts-pg-1}"
AIRFLOW_CONTAINER="${DTS_AIRFLOW_CONTAINER:-dts-airflow-scheduler}"
PLATFORM_DB="${DTS_PLATFORM_DB:-dts_platform}"
TARGET_DB="${DTS_TARGET_DB:-biadmin}"
PG_USER="${DTS_PG_USER:-postgres}"
JDBC_SOURCE_ID="${DTS_IT09_JDBC_SOURCE_ID:-a0000000-0000-0000-0000-000000000001}"
SOURCE_SCHEMA="s38_regression"
SOURCE_TABLE="src_jdbc_orders"
EXCHANGE_DIR="${DTS_IT09_EXCHANGE_DIR:-${ROOT_DIR}/services/dts-airflow/dags/uploads/s38-it09}"
AIRFLOW_FILE_DIR="${DTS_IT09_AIRFLOW_FILE_DIR:-/opt/airflow/dags/uploads/s38-it09}"
ADDAX_FILE_DIR="${DTS_IT09_ADDAX_FILE_DIR:-/opt/addax/jobs/uploads/s38-it09}"

for bin in docker jq sha256sum stat; do
  command -v "$bin" >/dev/null 2>&1 || {
    echo "missing required command: $bin" >&2
    exit 2
  }
done

psql_platform_scalar() {
  docker exec -i "$PG_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d "$PLATFORM_DB" -At -F '|' -c "$1"
}

psql_target_scalar() {
  docker exec -i "$PG_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d "$TARGET_DB" -At -F '|' -c "$1"
}

psql_target_exec() {
  docker exec -i "$PG_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d "$TARGET_DB" "$@"
}

json_value() {
  local path="$1"
  jq -r "$path // empty"
}

portal_token="${DTS_PORTAL_SESSION_TOKEN:-}"
if [[ -z "$portal_token" ]]; then
  portal_token="$(
    psql_platform_scalar "
      select access_token
      from portal_sessions
      where revoked_at is null
        and expires_at > now()
        and roles::text ~ 'ROLE_(ADMIN|OP_ADMIN|INST_DATA_OWNER|DEPT_DATA_OWNER|INST_LEADER|DEPT_LEADER)'
      order by created_at desc
      limit 1;
    "
  )"
fi

if [[ -z "$portal_token" ]]; then
  echo "no_active_infra_maintainer_session=true"
  exit 3
fi

platform_post() {
  local path="$1"
  local payload="$2"
  docker exec -i -e PORTAL_TOKEN="$portal_token" "$PLATFORM_CONTAINER" sh -lc \
    "curl -sS -H 'Content-Type: application/json' -H \"Cookie: portal_session=\$PORTAL_TOKEN\" -X POST --data-binary @- \"http://127.0.0.1:8081${path}\"" \
    <<<"$payload"
}

platform_post_empty() {
  local path="$1"
  docker exec -e PORTAL_TOKEN="$portal_token" "$PLATFORM_CONTAINER" sh -lc \
    "curl -sS -H \"Cookie: portal_session=\$PORTAL_TOKEN\" -X POST \"http://127.0.0.1:8081${path}\""
}

create_task() {
  local task_name="$1"
  local payload="$2"
  local body status task_id
  body="$(platform_post "/api/ingestion/tasks" "$payload")"
  status="$(json_value '.status' <<<"$body")"
  if [[ -z "$status" || "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "create_task_failed=${task_name}" >&2
    jq '{status,message,code}' <<<"$body" >&2 || printf '%s\n' "$body" >&2
    exit 4
  fi
  task_id="$(json_value '.data.id // .data.taskId // .data.task.id' <<<"$body")"
  if [[ -z "$task_id" ]]; then
    task_id="$(psql_platform_scalar "select id from ingestion_task where name = '${task_name}' order by id desc limit 1;")"
  fi
  if [[ -z "$task_id" ]]; then
    echo "created task ${task_name} but could not resolve id" >&2
    exit 4
  fi
  printf '%s' "$task_id"
}

wait_task_execution() {
  local label="$1"
  local task_id="$2"
  local row execution_db_id airflow_run_id status
  for _ in $(seq 1 90); do
    row="$(
      psql_platform_scalar "
        select id, coalesce(execution_id, ''), coalesce(status, '')
        from ingestion_execution
        where task_id = ${task_id}
        order by id desc
        limit 1;
      "
    )"
    IFS='|' read -r execution_db_id airflow_run_id status <<<"$row"
    if [[ "$status" == "success" ]]; then
      echo "${label}_execution_id=${execution_db_id}"
      echo "${label}_airflow_run_id=${airflow_run_id}"
      echo "${label}_execution_status=${status}"
      return
    fi
    if [[ "$status" == "failed" ]]; then
      echo "${label}_execution_id=${execution_db_id}"
      echo "${label}_execution_status=${status}"
      psql_platform_scalar "select coalesce(error_message, '') from ingestion_execution where id = ${execution_db_id};" >&2 || true
      exit 5
    fi
    sleep 2
  done
  echo "${label}_execution_timeout=true" >&2
  exit 5
}

trigger_task() {
  local label="$1"
  local task_id="$2"
  local body status
  body="$(platform_post_empty "/api/ingestion/tasks/${task_id}/execute/async")"
  status="$(json_value '.status' <<<"$body")"
  if [[ -z "$status" || "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "${label}_trigger_failed=true" >&2
    jq '{status,message,code}' <<<"$body" >&2 || printf '%s\n' "$body" >&2
    exit 5
  fi
  echo "${label}_trigger_status=${status}"
  wait_task_execution "$label" "$task_id"
}

assert_airflow_success() {
  local label="$1"
  local task_id="$2"
  local dag_id run_id airflow_task_id state
  dag_id="$(psql_platform_scalar "select airflow_dag_id from ingestion_task where id = ${task_id};")"
  run_id="$(psql_platform_scalar "select coalesce(execution_id, '') from ingestion_execution where task_id = ${task_id} order by id desc limit 1;")"
  airflow_task_id="$(
    docker exec "$AIRFLOW_CONTAINER" sh -lc "airflow tasks list '$dag_id' 2>/dev/null | awk 'NF {print \$1; exit}'"
  )"
  if [[ -z "$dag_id" || -z "$run_id" || -z "$airflow_task_id" ]]; then
    echo "${label}_airflow_metadata_missing=true" >&2
    exit 6
  fi
  state="$(
    docker exec "$AIRFLOW_CONTAINER" sh -lc "airflow tasks state '$dag_id' '$airflow_task_id' '$run_id' 2>/dev/null | tail -n 1 | tr -d '[:space:]'"
  )"
  echo "${label}_airflow_dag_id=${dag_id}"
  echo "${label}_airflow_task_id=${airflow_task_id}"
  echo "${label}_airflow_task_state=${state}"
  [[ "$state" == "success" ]]
}

prepare_jdbc_source() {
  psql_target_exec <<SQL
CREATE SCHEMA IF NOT EXISTS ${SOURCE_SCHEMA};
DROP TABLE IF EXISTS ${SOURCE_SCHEMA}.${SOURCE_TABLE};
CREATE TABLE ${SOURCE_SCHEMA}.${SOURCE_TABLE} (
  order_id text PRIMARY KEY,
  amount numeric(12, 2),
  updated_at timestamp
);
INSERT INTO ${SOURCE_SCHEMA}.${SOURCE_TABLE} (order_id, amount, updated_at) VALUES
  ('J-001', 12.50, timestamp '2026-06-12 01:00:00'),
  ('J-002', 20.00, timestamp '2026-06-12 01:05:00');
GRANT USAGE, CREATE ON SCHEMA ${SOURCE_SCHEMA} TO biadmin;
GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA ${SOURCE_SCHEMA} TO biadmin;
ALTER DEFAULT PRIVILEGES IN SCHEMA ${SOURCE_SCHEMA} GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON TABLES TO biadmin;
SQL
}

create_jdbc_task() {
  local suffix="$1"
  local target_table="s38_it09_jdbc_orders_${suffix}"
  local task_name="s38-it09-jdbc-${suffix}"
  local payload
  psql_target_exec -c "DROP TABLE IF EXISTS public.${target_table};" >/dev/null
  payload="$(
    jq -n \
      --arg taskName "$task_name" \
      --arg sourceId "$JDBC_SOURCE_ID" \
      --arg sourceTable "${SOURCE_SCHEMA}.${SOURCE_TABLE}" \
      --arg targetTable "$target_table" \
      '{
        taskName: $taskName,
        name: $taskName,
        description: "Sprint-38 IT-09 JDBC regression",
        source: {
          dataSourceId: $sourceId,
          type: "postgresqlreader",
          config: { readerType: "postgresqlreader", column: ["*"] }
        },
        destination: {
          usePlatformDefault: true,
          config: {
            connection: [{ table: [$targetTable] }],
            column: ["*"],
            writeMode: "insert"
          }
        },
        sync: { mode: "full_refresh", schedule: { type: "manual" } },
        streams: { selection: "manual", include: [$sourceTable] },
        airflow: { enabled: true },
        runNow: false
      }'
  )"
  create_task "$task_name" "$payload"
}

assert_jdbc_target() {
  local task_id="$1"
  local target_table="$2"
  local row rows min_id max_id total tagged
  row="$(
    psql_target_scalar "
      select
        count(*)::text,
        coalesce(min(order_id), ''),
        coalesce(max(order_id), ''),
        coalesce(sum(amount)::text, ''),
        count(*) filter (where _dts_task_id = '${task_id}')::text
      from public.${target_table};
    "
  )"
  IFS='|' read -r rows min_id max_id total tagged <<<"$row"
  echo "jdbc_target_table=public.${target_table}"
  echo "jdbc_target_rows=${rows}"
  echo "jdbc_target_min_order=${min_id}"
  echo "jdbc_target_max_order=${max_id}"
  echo "jdbc_target_amount_sum=${total}"
  echo "jdbc_target_tagged_rows=${tagged}"
  [[ "$rows" == "2" ]]
  [[ "$min_id" == "J-001" ]]
  [[ "$max_id" == "J-002" ]]
  [[ "$total" == "32.50" ]]
  [[ "$tagged" == "2" ]]
}

prepare_file_source() {
  local suffix="$1"
  local file_name="s38-it09-file-${suffix}.csv"
  local file_path="${EXCHANGE_DIR}/${file_name}"
  mkdir -p "$EXCHANGE_DIR" || return 1
  {
    printf 'order_id,amount,updated_at\n'
    printf 'F-001,9.75,2026-06-12 02:00:00\n'
    printf 'F-002,14.25,2026-06-12 02:05:00\n'
  } >"$file_path" || return 1
  chmod 0644 "$file_path" || return 1
  [[ -s "$file_path" ]] || return 1
  printf '%s' "$file_name"
}

create_file_task() {
  local suffix="$1"
  local file_name="$2"
  local target_table="s38_it09_file_orders_${suffix}"
  local task_name="s38-it09-file-${suffix}"
  local file_path="${EXCHANGE_DIR}/${file_name}"
  local airflow_path="${AIRFLOW_FILE_DIR}/${file_name}"
  local addax_path="${ADDAX_FILE_DIR}/${file_name}"
  local file_hash file_size payload
  file_hash="$(sha256sum "$file_path" | awk '{print $1}')"
  file_size="$(stat -c '%s' "$file_path")"
  psql_target_exec -c "DROP TABLE IF EXISTS public.${target_table};" >/dev/null
  payload="$(
    jq -n \
      --arg taskName "$task_name" \
      --arg targetTable "$target_table" \
      --arg airflowPath "$airflow_path" \
      --arg addaxPath "$addax_path" \
      --arg fileHash "$file_hash" \
      --arg fileName "$file_name" \
      --argjson fileSize "$file_size" \
      '{
        taskName: $taskName,
        name: $taskName,
        description: "Sprint-38 IT-09 file regression",
        source: {
          type: "txtfilereader",
          config: {
            _filePath: $airflowPath,
            _containerPath: $addaxPath,
            _fileType: "csv",
            _fileHash: $fileHash,
            _fileSize: $fileSize,
            _originalName: $fileName,
            _autoId: true,
            _fileColumns: [
              { name: "order_id", safeName: "order_id", type: "string", label: "order_id" },
              { name: "amount", safeName: "amount", type: "string", label: "amount" },
              { name: "updated_at", safeName: "updated_at", type: "string", label: "updated_at" }
            ]
          }
        },
        destination: {
          usePlatformDefault: true,
          config: {
            connection: [{ table: [$targetTable] }],
            column: ["*"],
            writeMode: "insert"
          }
        },
        sync: { mode: "full_refresh", schedule: { type: "manual" } },
        streams: { selection: "manual", include: [$targetTable] },
        airflow: { enabled: true },
        runNow: false
      }'
  )"
  create_task "$task_name" "$payload"
}

assert_file_target() {
  local task_id="$1"
  local target_table="$2"
  local row rows min_id max_id tagged
  row="$(
    psql_target_scalar "
      select
        count(*)::text,
        coalesce(min(order_id), ''),
        coalesce(max(order_id), ''),
        count(*) filter (where _dts_task_id = '${task_id}')::text
      from public.${target_table};
    "
  )"
  IFS='|' read -r rows min_id max_id tagged <<<"$row"
  echo "file_target_table=public.${target_table}"
  echo "file_target_rows=${rows}"
  echo "file_target_min_order=${min_id}"
  echo "file_target_max_order=${max_id}"
  echo "file_target_tagged_rows=${tagged}"
  [[ "$rows" == "2" ]]
  [[ "$min_id" == "F-001" ]]
  [[ "$max_id" == "F-002" ]]
  [[ "$tagged" == "2" ]]
}

main() {
  local suffix jdbc_task_id jdbc_target file_name file_task_id file_target
  suffix="$(printf '%s%03d' "$(date +%s)" "$((RANDOM % 1000))")"

  source_state="$(psql_platform_scalar "select coalesce(type, ''), coalesce(status, '') from infra_data_source where id = '${JDBC_SOURCE_ID}';")"
  if [[ -z "$source_state" ]]; then
    echo "jdbc_source_missing=${JDBC_SOURCE_ID}"
    exit 7
  fi
  echo "jdbc_source_id=${JDBC_SOURCE_ID}"

  prepare_jdbc_source
  jdbc_target="s38_it09_jdbc_orders_${suffix}"
  jdbc_task_id="$(create_jdbc_task "$suffix")"
  echo "jdbc_task_id=${jdbc_task_id}"
  trigger_task "jdbc" "$jdbc_task_id"
  assert_airflow_success "jdbc" "$jdbc_task_id"
  assert_jdbc_target "$jdbc_task_id" "$jdbc_target"

  if ! file_name="$(prepare_file_source "$suffix")"; then
    echo "file_fixture_prepare_failed=true" >&2
    exit 8
  fi
  echo "file_source_name=${file_name}"
  file_target="s38_it09_file_orders_${suffix}"
  file_task_id="$(create_file_task "$suffix" "$file_name")"
  echo "file_task_id=${file_task_id}"
  trigger_task "file" "$file_task_id"
  assert_airflow_success "file" "$file_task_id"
  assert_file_target "$file_task_id" "$file_target"
}

main "$@"

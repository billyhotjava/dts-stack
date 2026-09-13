#!/usr/bin/env bash
set -euo pipefail

if [[ "${RUN_LIVE:-0}" != "1" ]]; then
  echo "SKIP: set RUN_LIVE=1 to run PostgreSQL raw landing idempotency IT"
  exit 0
fi

PG_CONTAINER="${PG_CONTAINER:-v223-dts-pg-1}"
PG_DB="${PG_DB:-biadmin}"
PG_USER="${PG_USER:-biadmin}"
SCHEMA="dts_it_api_raw_$(date +%Y%m%d%H%M%S)_$$"

psql_exec() {
  docker exec -i "${PG_CONTAINER}" psql -X -U "${PG_USER}" -d "${PG_DB}" -v ON_ERROR_STOP=1 "$@"
}

cleanup() {
  psql_exec -qAt -c "DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE" >/dev/null 2>&1 || true
}
trap cleanup EXIT

psql_exec -qAt <<SQL
CREATE SCHEMA ${SCHEMA};
CREATE TABLE ${SCHEMA}.orders (
  id BIGSERIAL PRIMARY KEY,
  _dts_raw_record JSONB NOT NULL,
  _dts_source_system TEXT,
  _dts_source_resource TEXT,
  _dts_endpoint TEXT,
  _dts_import_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  _dts_batch_id TEXT,
  _dts_execution_id TEXT,
  _dts_page_no INTEGER,
  _dts_record_no INTEGER,
  _dts_cursor_value TEXT,
  _dts_record_hash TEXT NOT NULL
);
CREATE UNIQUE INDEX ux_${SCHEMA}_orders_hash
  ON ${SCHEMA}.orders (_dts_source_resource, _dts_record_hash);
SQL

first_inserted="$(
  psql_exec -qAt <<SQL
WITH inserted AS (
  INSERT INTO ${SCHEMA}.orders (
    _dts_raw_record, _dts_source_system, _dts_source_resource, _dts_endpoint, _dts_import_time,
    _dts_batch_id, _dts_execution_id, _dts_page_no, _dts_record_no, _dts_cursor_value, _dts_record_hash
  )
  VALUES
    ('{"id":1,"updatedAt":"2026-01-01T00:00:00Z"}'::jsonb, 'CRM', 'orders', 'https://api.example.test/orders', now(), 'batch-1', 'execution-1', 1, 1, '2026-01-01T00:00:00Z', 'hash-orders-1'),
    ('{"id":2,"updatedAt":"2026-01-01T00:01:00Z"}'::jsonb, 'CRM', 'orders', 'https://api.example.test/orders', now(), 'batch-1', 'execution-1', 1, 2, '2026-01-01T00:01:00Z', 'hash-orders-2')
  ON CONFLICT (_dts_source_resource, _dts_record_hash) DO NOTHING
  RETURNING 1
)
SELECT count(*) FROM inserted;
SQL
)"
after_first="$(psql_exec -qAt -c "SELECT count(*) FROM ${SCHEMA}.orders")"

second_inserted="$(
  psql_exec -qAt <<SQL
WITH inserted AS (
  INSERT INTO ${SCHEMA}.orders (
    _dts_raw_record, _dts_source_system, _dts_source_resource, _dts_endpoint, _dts_import_time,
    _dts_batch_id, _dts_execution_id, _dts_page_no, _dts_record_no, _dts_cursor_value, _dts_record_hash
  )
  VALUES
    ('{"id":1,"updatedAt":"2026-01-01T00:00:00Z"}'::jsonb, 'CRM', 'orders', 'https://api.example.test/orders', now(), 'batch-2', 'execution-2', 1, 1, '2026-01-01T00:00:00Z', 'hash-orders-1'),
    ('{"id":2,"updatedAt":"2026-01-01T00:01:00Z"}'::jsonb, 'CRM', 'orders', 'https://api.example.test/orders', now(), 'batch-2', 'execution-2', 1, 2, '2026-01-01T00:01:00Z', 'hash-orders-2')
  ON CONFLICT (_dts_source_resource, _dts_record_hash) DO NOTHING
  RETURNING 1
)
SELECT count(*) FROM inserted;
SQL
)"
after_second="$(psql_exec -qAt -c "SELECT count(*) FROM ${SCHEMA}.orders")"

echo "schema=${SCHEMA}"
echo "first_inserted=${first_inserted}"
echo "after_first=${after_first}"
echo "second_inserted=${second_inserted}"
echo "after_second=${after_second}"

if [[ "${first_inserted}" != "2" || "${after_first}" != "2" || "${second_inserted}" != "0" || "${after_second}" != "2" ]]; then
  echo "idempotency=FAIL"
  exit 1
fi

echo "idempotency=PASS"

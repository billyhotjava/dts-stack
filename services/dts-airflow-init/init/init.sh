#!/usr/bin/env bash
set -euo pipefail

echo "[airflow-init] Starting Airflow DB init..."

# Basic sanity for required env
: "${AIRFLOW__DATABASE__SQL_ALCHEMY_CONN:?AIRFLOW__DATABASE__SQL_ALCHEMY_CONN is required}"
: "${AIRFLOW_ADMIN_USER:=admin}"
: "${AIRFLOW_ADMIN_PASSWORD:?AIRFLOW_ADMIN_PASSWORD is required}"
: "${AIRFLOW_ADMIN_EMAIL:=admin@example.com}"

# Always run init (idempotent) because `airflow db check` may return 0 pre-init
echo "[airflow-init] Ensuring DB schema is initialized (airflow db init)"
airflow db init

# Create admin user (idempotent)
airflow users create \
  --username "${AIRFLOW_ADMIN_USER}" \
  --firstname Admin \
  --lastname User \
  --role Admin \
  --email "${AIRFLOW_ADMIN_EMAIL}" \
  --password "${AIRFLOW_ADMIN_PASSWORD}" || true

echo "[airflow-init] Initialization complete."

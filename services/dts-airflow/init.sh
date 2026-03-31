#!/bin/bash
# Airflow init: fix permissions, migrate DB, create admin user.
# Runs as root inside dts-airflow-init container before other Airflow services start.
set -e

echo "[airflow-init] Fixing bind-mount permissions for airflow user (uid=50000)..."
find /opt/airflow/dags /opt/airflow/plugins /opt/airflow/extra /opt/airflow/config \
  -type f ! -perm /o+r -exec chmod o+r {} + 2>/dev/null || true
find /opt/airflow/dags /opt/airflow/plugins /opt/airflow/extra /opt/airflow/config \
  -type d ! -perm /o+rx -exec chmod o+rx {} + 2>/dev/null || true

rm -f /opt/airflow/airflow.cfg
airflow db migrate

if airflow --help 2>/dev/null | grep -q "users"; then
  airflow users create \
    --username "${AIRFLOW_ADMIN_USERNAME}" \
    --password "${AIRFLOW_ADMIN_PASSWORD}" \
    --firstname "${AIRFLOW_ADMIN_FIRSTNAME}" \
    --lastname "${AIRFLOW_ADMIN_LASTNAME}" \
    --role Admin \
    --email "${AIRFLOW_ADMIN_EMAIL}" || true
fi

echo "[airflow-init] Done."

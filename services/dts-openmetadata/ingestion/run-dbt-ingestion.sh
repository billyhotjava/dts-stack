#!/bin/sh
set -eu

MANIFEST="/opt/dbt/target/manifest.json"
RUN_RESULTS="/opt/dbt/target/run_results.json"
CATALOG="/opt/dbt/target/catalog.json"

if [ ! -f "${MANIFEST}" ] || [ ! -f "${RUN_RESULTS}" ]; then
  echo "[openmetadata-ingestion] dbt artifacts not found; skipping ingestion." >&2
  exit 0
fi

if [ ! -f "${CATALOG}" ]; then
  echo "[openmetadata-ingestion] catalog.json not found; continuing with available artifacts." >&2
fi

metadata ingest -c /opt/openmetadata/ingestion/dbt.yml

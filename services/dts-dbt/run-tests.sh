#!/usr/bin/env bash
set -euo pipefail

cd /opt/dbt

if [[ ! -f dbt_project.yml ]]; then
  echo "[dbt] Missing dbt_project.yml in /opt/dbt." >&2
  echo "[dbt] Mount your dbt project into services/dts-dbt." >&2
  exit 1
fi

dbt deps --profiles-dir /root/.dbt

dbt test --profiles-dir /root/.dbt

dbt docs generate --profiles-dir /root/.dbt

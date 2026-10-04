#!/bin/sh
set -eu

CONFIG_SRC="/opt/openmetadata/ingestion/postgres.yml"
CONFIG_TMP="/tmp/openmetadata-postgres.yml"
export CONFIG_SRC CONFIG_TMP
INGEST_DATABASE="${OPENMETADATA_INGEST_DATABASE:-}"
FORBIDDEN_DATABASES="${OPENMETADATA_FORBIDDEN_DATABASES:-dts_platform,dts-platform,dts_admin,dts-admin,dts_common,dts-common,dts_analytics,dts-analytics,dts_keycloak,dts-keycloak,openmetadata_db,openmetadata,airflow,dts_ranger,dts-ranger}"
ALLOW_NO_AUTH="${OPENMETADATA_ALLOW_NO_AUTH:-false}"
export ALLOW_NO_AUTH INGEST_DATABASE FORBIDDEN_DATABASES

normalized_db=$(printf '%s' "${INGEST_DATABASE}" | tr '[:upper:]' '[:lower:]' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
normalized_forbidden=$(printf '%s' "${FORBIDDEN_DATABASES}" | tr '[:upper:]' '[:lower:]' | sed 's/[[:space:]]//g')

if [ -z "${normalized_db}" ]; then
  echo "[openmetadata-ingestion] OPENMETADATA_INGEST_DATABASE is empty; refusing PostgreSQL metadata ingestion." >&2
  echo "[openmetadata-ingestion] Set DTS_OPENMETADATA_INGEST_DATABASE to a warehouse/analytics database, for example biadmin." >&2
  exit 2
fi

case ",${normalized_forbidden}," in
  *,"${normalized_db}",*)
    echo "[openmetadata-ingestion] database '${INGEST_DATABASE}' is forbidden for metadata ingestion." >&2
    echo "[openmetadata-ingestion] Platform business/internal databases must not enter the warehouse analytics catalog; set DTS_OPENMETADATA_INGEST_DATABASE to an allowed warehouse database." >&2
    exit 2
    ;;
esac

echo "[openmetadata-ingestion] target_database=${INGEST_DATABASE}" >&2

if [ -z "${OPENMETADATA_AUTH_TOKEN:-}" ] && [ "${ALLOW_NO_AUTH}" != "true" ]; then
  echo "[openmetadata-ingestion] OPENMETADATA_AUTH_TOKEN empty; failing ingestion (set DTS_OPENMETADATA_ALLOW_NO_AUTH=true only for explicit no-auth deployments)." >&2
  exit 2
fi

if [ -z "${OPENMETADATA_AUTH_TOKEN:-}" ]; then
  echo "[openmetadata-ingestion] auth_mode=no-auth" >&2
else
  echo "[openmetadata-ingestion] auth_mode=token" >&2
fi

python3 - <<'PY'
import os
from pathlib import Path

src = Path(os.environ.get("CONFIG_SRC", "/opt/openmetadata/ingestion/postgres.yml"))
out = Path(os.environ.get("CONFIG_TMP", "/tmp/openmetadata-postgres.yml"))

text = src.read_text(encoding="utf-8")
text = os.path.expandvars(text)

token = os.environ.get("OPENMETADATA_AUTH_TOKEN", "")
if not token and os.environ.get("ALLOW_NO_AUTH", "false").lower() == "true":
    lines = text.splitlines()
    cleaned = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("jwtToken:"):
            indent = line[: len(line) - len(line.lstrip())]
            cleaned.append(f'{indent}jwtToken: ""')
            continue
        cleaned.append(line)
    text = "\n".join(cleaned) + "\n"

out.write_text(text, encoding="utf-8")
print(f"[openmetadata-ingestion] Using config: {out}")
PY

metadata ingest -c "${CONFIG_TMP}"

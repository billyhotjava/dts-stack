#!/bin/sh
set -eu

MANIFEST="${DBT_MANIFEST_PATH:-/opt/dbt/target/manifest.json}"
RUN_RESULTS="${DBT_RUN_RESULTS_PATH:-/opt/dbt/target/run_results.json}"
CATALOG="${DBT_CATALOG_PATH:-/opt/dbt/target/catalog.json}"
CONFIG_SRC="/opt/openmetadata/ingestion/dbt.yml"
CONFIG_TMP="/tmp/openmetadata-dbt.yml"
export CONFIG_SRC CONFIG_TMP
FORBIDDEN_DATABASES="${OPENMETADATA_FORBIDDEN_DATABASES:-dts_platform,dts-platform,dts_admin,dts-admin,dts_common,dts-common,dts_analytics,dts-analytics,dts_keycloak,dts-keycloak,openmetadata_db,openmetadata,airflow,dts_ranger,dts-ranger}"
ALLOW_NO_AUTH="${OPENMETADATA_ALLOW_NO_AUTH:-false}"
export ALLOW_NO_AUTH FORBIDDEN_DATABASES MANIFEST CATALOG

if [ ! -f "${MANIFEST}" ] || [ ! -f "${RUN_RESULTS}" ]; then
  echo "[openmetadata-ingestion] status=skipped reason=dbt_artifacts_not_found" >&2
  exit 0
fi

if [ ! -f "${CATALOG}" ]; then
  echo "[openmetadata-ingestion] catalog.json not found; continuing with available artifacts." >&2
fi

python3 - <<'PY'
import json
import os
import re
import sys
from pathlib import Path

forbidden = {
    item.strip().lower()
    for item in os.environ.get("FORBIDDEN_DATABASES", "").split(",")
    if item.strip()
}
field_names = {"database", "relation_name", "fqn", "fullyqualifiedname", "table_fqn"}
artifacts = [
    Path(os.environ.get("MANIFEST", "/opt/dbt/target/manifest.json")),
    Path(os.environ.get("CATALOG", "/opt/dbt/target/catalog.json")),
]
matches = []


def normalized_segments(value):
    text = str(value).strip().lower()
    text = re.sub(r'["`\[\]\s]', "", text)
    return [segment for segment in text.split(".") if segment]


def references_forbidden_database(field_name, value):
    segments = normalized_segments(value)
    if not segments:
        return False
    if field_name == "database":
        return segments[0] in forbidden
    if field_name == "relation_name":
        return segments[0] in forbidden
    return (len(segments) > 1 and segments[1] in forbidden) or segments[0] in forbidden


def walk(value, path):
    if len(matches) >= 5:
        return
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}" if path else str(key)
            lowered_key = str(key).lower()
            if lowered_key in field_names and isinstance(child, (str, int, float)):
                if references_forbidden_database(lowered_key, child):
                    matches.append(f"{child_path}={child}")
                    continue
            walk(child, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            walk(child, f"{path}[{index}]")


for artifact in artifacts:
    if not artifact.is_file():
        continue
    try:
        walk(json.loads(artifact.read_text(encoding="utf-8")), artifact.name)
    except json.JSONDecodeError as exc:
        print(f"[openmetadata-ingestion] invalid dbt artifact {artifact}: {exc}", file=sys.stderr)
        sys.exit(2)

if matches:
    print("[openmetadata-ingestion] dbt artifacts reference forbidden platform business/internal databases.", file=sys.stderr)
    for match in matches:
        print(f"[openmetadata-ingestion] forbidden_reference={match}", file=sys.stderr)
    sys.exit(2)
PY

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

src = Path(os.environ.get("CONFIG_SRC", "/opt/openmetadata/ingestion/dbt.yml"))
out = Path(os.environ.get("CONFIG_TMP", "/tmp/openmetadata-dbt.yml"))

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

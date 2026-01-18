#!/bin/sh
set -eu

CONFIG_SRC="/opt/openmetadata/ingestion/postgres.yml"
CONFIG_TMP="/tmp/openmetadata-postgres.yml"
export CONFIG_SRC CONFIG_TMP
ALLOW_NO_AUTH="${OPENMETADATA_ALLOW_NO_AUTH:-false}"
export ALLOW_NO_AUTH

python3 - <<'PY'
import os
from pathlib import Path

src = Path(os.environ.get("CONFIG_SRC", "/opt/openmetadata/ingestion/postgres.yml"))
out = Path(os.environ.get("CONFIG_TMP", "/tmp/openmetadata-postgres.yml"))

text = src.read_text(encoding="utf-8")
text = os.path.expandvars(text)

token = os.environ.get("OPENMETADATA_AUTH_TOKEN", "")
if not token and os.environ.get("ALLOW_NO_AUTH", "false").lower() == "true":
    text = text.replace("authProvider: openmetadata", "authProvider: no-auth")
    lines = text.splitlines()
    cleaned = []
    skip_next = False
    for line in lines:
        if skip_next:
            skip_next = False
            continue
        stripped = line.strip()
        if stripped.startswith("securityConfig:"):
            skip_next = True
            continue
        if stripped.startswith("jwtToken:"):
            continue
        cleaned.append(line)
    text = "\n".join(cleaned) + "\n"

out.write_text(text, encoding="utf-8")
print(f"[openmetadata-ingestion] Using config: {out}")
PY

if [ -z "${OPENMETADATA_AUTH_TOKEN:-}" ] && [ "${ALLOW_NO_AUTH}" != "true" ]; then
  echo "[openmetadata-ingestion] OPENMETADATA_AUTH_TOKEN empty; skip ingestion (set OPENMETADATA_ALLOW_NO_AUTH=true to force no-auth)." >&2
  exit 0
fi

metadata ingest -c "${CONFIG_TMP}"

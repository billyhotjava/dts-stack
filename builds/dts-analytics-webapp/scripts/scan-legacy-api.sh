#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
WEBAPP_DIR="${ROOT_DIR}/source/dts-analytics-webapp"
LEGACY_DIR="${WEBAPP_DIR}/legacy/frontend_client"

if [[ ! -d "${LEGACY_DIR}" ]]; then
  echo "[scan-legacy-api] ERROR: missing ${LEGACY_DIR}" >&2
  echo "[scan-legacy-api] Run: bash builds/dts-analytics-webapp/scripts/extract-metabase-ui.sh" >&2
  exit 1
fi

OUT_FILE="${WEBAPP_DIR}/legacy/api-endpoints.txt"

python3 - <<'PY'
import re
from pathlib import Path

legacy_dir = Path("source/dts-analytics-webapp/legacy/frontend_client")
out_file = Path("source/dts-analytics-webapp/legacy/api-endpoints.txt")

patterns = [
    re.compile(r'"/api/[^"\\s]+' ),
    re.compile(r'"/auth/[^"\\s]+' ),
]

endpoints = set()
for path in legacy_dir.rglob("*.js"):
    try:
        text = path.read_text("utf-8", errors="ignore")
    except Exception:
        continue
    for pat in patterns:
        for m in pat.finditer(text):
            endpoints.add(m.group(0).strip('"'))

out_file.parent.mkdir(parents=True, exist_ok=True)
out_file.write_text("\n".join(sorted(endpoints)) + ("\n" if endpoints else ""), encoding="utf-8")
print(f"[scan-legacy-api] endpoints={len(endpoints)} -> {out_file}")
PY

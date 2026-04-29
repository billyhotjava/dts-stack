#!/usr/bin/env bash
# Sanitize plaintext DB passwords from generated Airflow DAG files.
#
# Background:
#   AirflowDagService used to embed the data-lake DB password as a fallback
#   string in DAG files (`password=os.getenv("DTS_TARGET_DB_PASSWORD", "<plaintext>")`).
#   The Java template has been fixed to require the env var (no fallback),
#   but already-generated DAG files on disk still contain the plaintext.
#
# What this script does:
#   1. Backs up each affected DAG file to <file>.pre-sanitize.bak
#   2. Replaces the leaky line with the strict form:
#      `password=os.environ["DTS_TARGET_DB_PASSWORD"],`
#   3. Reports which files were changed.
#
# Prerequisites:
#   - Run docker compose with the updated docker-compose-app.yml (or legacy)
#     so DTS_TARGET_DB_PASSWORD is provisioned to scheduler/triggerer/webserver,
#     OTHERWISE the cleaned DAGs will KeyError at runtime.
#
# Usage:
#   sudo bash scripts/security/sanitize-dag-passwords.sh [DAG_DIR]
#   DAG_DIR defaults to ./services/dts-airflow/dags
set -euo pipefail

DAG_DIR="${1:-$(cd "$(dirname "$0")/../.." && pwd)/services/dts-airflow/dags}"
if [[ ! -d "$DAG_DIR" ]]; then
  echo "ERROR: DAG dir not found: $DAG_DIR" >&2
  exit 1
fi

cleaned=0
total=0
while IFS= read -r f; do
  total=$((total + 1))
  if grep -q 'password=os.getenv("DTS_TARGET_DB_PASSWORD"' "$f"; then
    cp -p "$f" "${f}.pre-sanitize.bak"
    python3 - "$f" <<'PY'
import re, sys
p = sys.argv[1]
with open(p, "r") as fh:
    src = fh.read()
new = re.sub(
    r'password=os\.getenv\("DTS_TARGET_DB_PASSWORD",\s*"[^"]*"\)',
    'password=os.environ["DTS_TARGET_DB_PASSWORD"]',
    src,
)
with open(p, "w") as fh:
    fh.write(new)
PY
    cleaned=$((cleaned + 1))
    echo "cleaned: $f (backup at ${f}.pre-sanitize.bak)"
  fi
done < <(find "$DAG_DIR" -maxdepth 1 -type f -name "*.py" 2>/dev/null)

echo
echo "Scanned: $total file(s), cleaned: $cleaned file(s)."
echo
echo "Next steps:"
echo "  1. Restart Airflow services so DTS_TARGET_DB_PASSWORD is in process env:"
echo "       docker compose -f docker-compose-app.yml restart \\"
echo "         dts-airflow-scheduler dts-airflow-triggerer dts-airflow-webserver"
echo "  2. Trigger one DAG run to confirm tasks succeed."
echo "  3. After verifying, remove .pre-sanitize.bak files:"
echo "       find $DAG_DIR -maxdepth 1 -name '*.pre-sanitize.bak' -delete"

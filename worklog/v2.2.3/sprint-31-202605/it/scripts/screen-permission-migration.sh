#!/usr/bin/env bash
set -euo pipefail

ANALYTICS_BASE_URL="${ANALYTICS_BASE_URL:-http://127.0.0.1:18083}"

echo "[1/1] migrate analytics_screen_access to platform asset_grant"
curl -fsS -X POST "${ANALYTICS_BASE_URL}/api/screens/admin/migrate-local-grants"
echo

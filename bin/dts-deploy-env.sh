#!/usr/bin/env bash
# =============================================================================
# dts-deploy-env.sh — Source this file to set API_BASE, TOKEN, SOURCE_DATA_SOURCE_ID
#
# Usage:
#   source bin/dts-deploy-env.sh
#   bin/dts-deploy --package <zip> --plan-name <name> --skip-existing --run
#
# Environment (all optional, will prompt if not set):
#   DTS_DOMAIN       — Base domain (default: from .env BASE_DOMAIN)
#   DTS_USERNAME     — Login username (default: opadmin)
#   DTS_PASSWORD     — Login password (prompted if not set)
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
if [[ ! -f "${PROJECT_ROOT}/.env" && -f ".env" ]]; then
  PROJECT_ROOT="$(pwd)"
fi

# Read BASE_DOMAIN from .env if not set
if [[ -z "${DTS_DOMAIN:-}" && -f "${PROJECT_ROOT}/.env" ]]; then
  DTS_DOMAIN=$(grep '^BASE_DOMAIN=' "${PROJECT_ROOT}/.env" | cut -d= -f2 | tr -d '"' | tr -d "'")
fi
DTS_DOMAIN="${DTS_DOMAIN:-localhost}"

# Username: use env or prompt
if [[ -z "${DTS_USERNAME:-}" ]]; then
  printf "[env] 用户名 (default: opadmin): "
  read -r DTS_USERNAME
  DTS_USERNAME="${DTS_USERNAME:-opadmin}"
fi

# Password: use env or prompt (hidden input)
if [[ -z "${DTS_PASSWORD:-}" ]]; then
  printf "[env] 密码 (%s): " "${DTS_USERNAME}"
  stty -echo 2>/dev/null; read -r DTS_PASSWORD; stty echo 2>/dev/null
  echo
fi

if [[ -z "$DTS_PASSWORD" ]]; then
  echo "[env] ERROR: 密码不能为空"
  return 1 2>/dev/null || exit 1
fi

# 1. API_BASE (without /api suffix — dts-common.sh appends /api automatically)
export API_BASE="https://bi.${DTS_DOMAIN}"
echo "[env] API_BASE=${API_BASE}"

# 2. TOKEN (via platform login API)
_FULL_API="${API_BASE%/}/api"
echo "[env] Authenticating as ${DTS_USERNAME}..."
TOKEN_RESP=$(curl -sk -X POST "${_FULL_API}/keycloak/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${DTS_USERNAME}\",\"password\":\"${DTS_PASSWORD}\"}" 2>/dev/null)

# Clear password from memory
unset DTS_PASSWORD

export TOKEN=$(echo "$TOKEN_RESP" | python3 -c "
import sys,json
try:
    d = json.loads(sys.stdin.read())
    data = d.get('data', d)
    if isinstance(data, dict):
        print(data.get('accessToken', data.get('access_token', '')))
    else:
        print('')
except: print('')
" 2>/dev/null)

if [[ -z "$TOKEN" ]]; then
  echo "[env] ERROR: 认证失败. Response: ${TOKEN_RESP:0:200}"
  return 1 2>/dev/null || exit 1
fi
echo "[env] TOKEN=...$(echo "$TOKEN" | tail -c 20)"

# 3. SOURCE_DATA_SOURCE_ID — get first available data source
DS_RESP=$(curl -sk "${_FULL_API}/infra/data-sources" \
  -H "Authorization: Bearer ${TOKEN}" 2>/dev/null)

export SOURCE_DATA_SOURCE_ID=$(echo "$DS_RESP" | python3 -c "
import sys,json
try:
    resp = json.loads(sys.stdin.read())
    ds = resp.get('data', resp) if isinstance(resp, dict) else resp
    if isinstance(ds, list) and ds:
        print(ds[0].get('id',''))
    else:
        print('')
except: print('')
" 2>/dev/null)

if [[ -z "$SOURCE_DATA_SOURCE_ID" ]]; then
  echo "[env] WARNING: 未找到数据源，请手动设置 SOURCE_DATA_SOURCE_ID"
  export SOURCE_DATA_SOURCE_ID="00000000-0000-0000-0000-000000000000"
else
  echo "[env] SOURCE_DATA_SOURCE_ID=${SOURCE_DATA_SOURCE_ID}"
fi

echo "[env] Ready."

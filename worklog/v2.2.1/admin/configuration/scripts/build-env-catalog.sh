#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/.env}"
OUT_DIR="${2:-$ROOT_DIR/worklog/v2.2.1/admin/configuration/raw}"
OUT_FILE="$OUT_DIR/env-catalog.csv"

mkdir -p "$OUT_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "env file not found: $ENV_FILE" >&2
  exit 1
fi

classify_key() {
  local key="$1"
  if [[ "$key" =~ ^(DTS_AIRFLOW_.*|DTS_OPENMETADATA_.*|DTS_PLATFORM_OPENMETADATA_.*|DTS_MDM_GATEWAY_.*|DTS_SECURITY_IP_ALLOWLIST_.*|ADMIN_ALLOWED_IPS|ADMIN_BACKUP_IPS|ADMIN_WHITELIST_CIDRS)$ ]]; then
    echo "visual_runtime"
    return
  fi
  if [[ "$key" =~ ^(OAUTH2_.*|OIDC_ISSUER_URI|DTS_PKI_.*|ANALYTICS_OIDC_.*|ANALYTICS_ENCRYPTION_SECRET)$ ]]; then
    echo "visual_restart"
    return
  fi
  if [[ "$key" =~ ^(IMAGE_.*|HOST_.*|BASE_DOMAIN|TLS_PORT|DTS_RUNTIME_.*|DOCKER_.*|DEPLOY_MODE|LEGACY_STACK|PG_SUPER_.*|PG_DB_.*|PG_USER_.*|PG_PWD_.*|AIRFLOW_FERNET_KEY|KC_ADMIN|KC_ADMIN_PWD)$ ]]; then
    echo "env_only"
    return
  fi
  echo "review_required"
}

sensitivity() {
  local key="$1"
  local lower="${key,,}"
  if [[ "$lower" =~ (password|secret|token|pwd|auth) ]] || [[ "$lower" =~ (_key$|_key_|^key_|^pg_pwd_) ]]; then
    echo "sensitive"
  else
    echo "normal"
  fi
}

printf "key,class,sensitivity,recommended_owner\n" >"$OUT_FILE"

while IFS='=' read -r key _; do
  [[ -z "$key" ]] && continue
  [[ "$key" =~ ^# ]] && continue
  [[ ! "$key" =~ ^[A-Z0-9_]+$ ]] && continue
  cls="$(classify_key "$key")"
  sens="$(sensitivity "$key")"
  owner="platform"
  if [[ "$cls" == "env_only" ]]; then
    owner="ops"
  elif [[ "$key" =~ ^(OAUTH2_|OIDC_ISSUER_URI|DTS_PKI_) ]]; then
    owner="security"
  elif [[ "$key" =~ ^DTS_MDM_GATEWAY_ ]]; then
    owner="admin"
  fi
  printf "%s,%s,%s,%s\n" "$key" "$cls" "$sens" "$owner" >>"$OUT_FILE"
done <"$ENV_FILE"

echo "generated: $OUT_FILE"

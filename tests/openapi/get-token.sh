#!/usr/bin/env bash
# 从 Keycloak 换取 access_token（password grant）。
#
# 用法：
#   # source：把 token 写入 ADMIN_TOKEN / PLATFORM_TOKEN
#   source tests/openapi/get-token.sh --target admin    <三员用户名> <密码>
#   source tests/openapi/get-token.sh --target platform <平台用户>   <密码>
#
#   # 直接执行：token 打印到 stdout
#   TOKEN=$(bash tests/openapi/get-token.sh --target admin alice pass)
#
# admin 与 platform 独立登录体系：
#   admin 侧：三员账号 (SYS_ADMIN/AUTH_ADMIN/AUDITOR_ADMIN)，默认 client = dts-admin
#   platform 侧：平台账号（访问 /v3/api-docs 需 ROLE_ADMIN），默认 client = dts-platform
#
# Keycloak client 必须启用 "Direct access grants"。

# 检测是否被 source
_sourced=0
(return 0 2>/dev/null) && _sourced=1

# 当被 source 时不能 set -e / exit，否则会把用户 shell 一起杀掉
_die() {
  echo "$@" >&2
  if [[ "${_sourced}" == "1" ]]; then return 1; else exit 1; fi
}

_get_token_main() {
  local TARGET="" KC_USER="" KC_PASS=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --target) TARGET="$2"; shift 2 ;;
      -h|--help)
        sed -n '2,18p' "${BASH_SOURCE[0]}"
        return 0 ;;
      --) shift; break ;;
      -*) _die "Unknown flag: $1"; return 2 ;;
      *)  break ;;
    esac
  done
  KC_USER="${1:-}"
  KC_PASS="${2:-}"

  if [[ -z "${TARGET}" || -z "${KC_USER}" || -z "${KC_PASS}" ]]; then
    _die "usage: get-token.sh --target admin|platform <username> <password>"
    return 2
  fi

  local EXPORT_VAR ENV_CLIENT_ID ENV_CLIENT_SECRET
  case "${TARGET}" in
    admin)
      EXPORT_VAR="ADMIN_TOKEN"
      ENV_CLIENT_ID="${OAUTH2_ADMIN_CLIENT_ID:-}"
      ENV_CLIENT_SECRET="${OAUTH2_ADMIN_CLIENT_SECRET:-}"
      ;;
    platform)
      EXPORT_VAR="PLATFORM_TOKEN"
      ENV_CLIENT_ID="${OAUTH2_PLATFORM_CLIENT_ID:-}"
      ENV_CLIENT_SECRET="${OAUTH2_PLATFORM_CLIENT_SECRET:-}"
      ;;
    *) _die "invalid --target: ${TARGET} (admin|platform)"; return 2 ;;
  esac

  # 自动从仓库根的 .env 读取 client id/secret（如果 shell 还没 export）
  local _script_dir
  _script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)" || _script_dir=""
  local repo_env="${_script_dir%/tests/openapi}/.env"
  if [[ -f "${repo_env}" && ( -z "${ENV_CLIENT_ID}" || -z "${ENV_CLIENT_SECRET}" ) ]]; then
    local key_id key_secret
    case "${TARGET}" in
      admin)    key_id="OAUTH2_ADMIN_CLIENT_ID";    key_secret="OAUTH2_ADMIN_CLIENT_SECRET" ;;
      platform) key_id="OAUTH2_PLATFORM_CLIENT_ID"; key_secret="OAUTH2_PLATFORM_CLIENT_SECRET" ;;
    esac
    [[ -z "${ENV_CLIENT_ID}"     ]] && ENV_CLIENT_ID=$(     grep -E "^${key_id}="     "${repo_env}" | tail -1 | cut -d= -f2-)
    [[ -z "${ENV_CLIENT_SECRET}" ]] && ENV_CLIENT_SECRET=$( grep -E "^${key_secret}=" "${repo_env}" | tail -1 | cut -d= -f2-)
  fi

  local ISSUER CLIENT_ID CLIENT_SECRET INSECURE_FLAG
  ISSUER="${OIDC_ISSUER_URI:-https://sso.yuzhicloud.com/realms/S10}"
  CLIENT_ID="${OIDC_CLIENT_ID:-${ENV_CLIENT_ID:-dts-system}}"
  CLIENT_SECRET="${OIDC_CLIENT_SECRET:-${ENV_CLIENT_SECRET:-}}"
  INSECURE_FLAG=""
  [[ -n "${INSECURE:-}" ]] && INSECURE_FLAG="-k"

  local body=(
    --data-urlencode "grant_type=password"
    --data-urlencode "username=${KC_USER}"
    --data-urlencode "password=${KC_PASS}"
    --data-urlencode "client_id=${CLIENT_ID}"
    --data-urlencode "scope=openid"
  )
  [[ -n "${CLIENT_SECRET}" ]] && body+=(--data-urlencode "client_secret=${CLIENT_SECRET}")

  if [[ -n "${DEBUG:-}" ]]; then
    echo "[debug] issuer=${ISSUER}" >&2
    echo "[debug] client_id=${CLIENT_ID}" >&2
    echo "[debug] client_secret_len=${#CLIENT_SECRET} (source: ${OIDC_CLIENT_SECRET:+env:OIDC_CLIENT_SECRET}${OIDC_CLIENT_SECRET:-${ENV_CLIENT_SECRET:+.env}})" >&2
    echo "[debug] username=${KC_USER} password_len=${#KC_PASS}" >&2
  fi

  local resp
  if ! resp=$(curl -sS --fail-with-body ${INSECURE_FLAG} -X POST \
      "${ISSUER%/}/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      "${body[@]}" 2>&1); then
    echo "[get-token] request failed:" >&2
    echo "${resp}" >&2
    echo "[hint] 用 DEBUG=1 source ... 再跑一次查看 client_id / secret_len / password_len" >&2
    echo "[hint] 也可能是 Keycloak brute-force 暂时锁了用户：Users -> 查看是否 Temporarily disabled" >&2
    return 1
  fi

  local token
  if ! token=$(printf '%s' "${resp}" | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])" 2>/dev/null); then
    echo "[get-token] response has no access_token:" >&2
    echo "${resp}" >&2
    return 1
  fi

  if [[ "${_sourced}" == "1" ]]; then
    export "${EXPORT_VAR}=${token}"
    echo "[get-token] ${EXPORT_VAR} exported (length=${#token}, client=${CLIENT_ID})" >&2
  else
    printf '%s\n' "${token}"
  fi
}

_get_token_main "$@"

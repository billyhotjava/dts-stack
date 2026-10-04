#!/usr/bin/env bash
# 拉取 dts-admin / dts-platform 的 OpenAPI 3 规范并写入 snapshots/。
# admin 与 platform 是独立认证体系，必须分别提供 token：
#   ADMIN_TOKEN     —— 三员账号的 access_token
#   PLATFORM_TOKEN  —— 平台 ROLE_ADMIN 账号的 access_token
# 推荐先：source tests/openapi/get-token.sh --target admin    <user> <pwd>
#         source tests/openapi/get-token.sh --target platform <user> <pwd>

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${SCRIPT_DIR}/snapshots"
mkdir -p "${OUT_DIR}"

MODE="direct"
INSECURE=""
ONLY=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) MODE="$2"; shift 2 ;;
    --only) ONLY="$2"; shift 2 ;;   # admin | platform
    --admin-token)    ADMIN_TOKEN="$2"; shift 2 ;;
    --platform-token) PLATFORM_TOKEN="$2"; shift 2 ;;
    --insecure|-k) INSECURE="-k"; shift ;;
    -h|--help)
      cat <<EOF
Usage: $0 [--mode direct|traefik] [--only admin|platform] [--insecure]
       [--admin-token JWT] [--platform-token JWT]

环境变量：
  ADMIN_TOKEN        admin 侧 Bearer token（三员角色）
  PLATFORM_TOKEN     platform 侧 Bearer token（ROLE_ADMIN）
  ADMIN_DIRECT_URL   覆盖 admin 直连 URL（默认 http://127.0.0.1:18081/v3/api-docs）
  PLATFORM_DIRECT_URL 覆盖 platform 直连 URL（默认 http://127.0.0.1:18082/v3/api-docs）
  HOST_ADMIN_UI / HOST_PLATFORM_UI   traefik 模式所需域名
EOF
      exit 0 ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

ADMIN_TOKEN="${ADMIN_TOKEN:-}"
PLATFORM_TOKEN="${PLATFORM_TOKEN:-}"

case "${MODE}" in
  direct)
    ADMIN_URL="${ADMIN_DIRECT_URL:-http://127.0.0.1:18081/v3/api-docs}"
    PLATFORM_URL="${PLATFORM_DIRECT_URL:-http://127.0.0.1:18082/v3/api-docs}"
    ;;
  traefik)
    : "${HOST_ADMIN_UI:?set HOST_ADMIN_UI, e.g. biadmin.example.com}"
    : "${HOST_PLATFORM_UI:?set HOST_PLATFORM_UI, e.g. bi.example.com}"
    ADMIN_URL="https://${HOST_ADMIN_UI}/api/v3/api-docs"
    PLATFORM_URL="https://${HOST_PLATFORM_UI}/api/v3/api-docs"
    ;;
  *) echo "Invalid --mode: ${MODE}" >&2; exit 2 ;;
esac

fetch() {
  local name="$1" url="$2" token="$3"
  local out="${OUT_DIR}/${name}.json"
  if [[ -z "${token}" ]]; then
    echo "[skip]  ${name}: 未提供 token，跳过。请先 source get-token.sh --target ${name#dts-}" >&2
    return 0
  fi
  echo "[fetch] ${name} <- ${url}"
  curl --fail-with-body -sS ${INSECURE} \
    -H "Authorization: Bearer ${token}" \
    "${url}" -o "${out}.tmp"
  python3 -c "import json; d=json.load(open('${out}.tmp')); assert d.get('openapi','').startswith('3'), 'not openapi3'; json.dump(d, open('${out}','w'), indent=2, ensure_ascii=False, sort_keys=True)"
  rm -f "${out}.tmp"
  local count
  count=$(python3 -c "import json; print(len(json.load(open('${out}'))['paths']))")
  echo "[ok]    ${name}: ${count} paths -> ${out}"
}

case "${ONLY}" in
  admin)    fetch "dts-admin"    "${ADMIN_URL}"    "${ADMIN_TOKEN}" ;;
  platform) fetch "dts-platform" "${PLATFORM_URL}" "${PLATFORM_TOKEN}" ;;
  "")
    fetch "dts-admin"    "${ADMIN_URL}"    "${ADMIN_TOKEN}"
    fetch "dts-platform" "${PLATFORM_URL}" "${PLATFORM_TOKEN}"
    ;;
  *) echo "Invalid --only: ${ONLY}" >&2; exit 2 ;;
esac

echo
echo "完成。产物位于: ${OUT_DIR}"

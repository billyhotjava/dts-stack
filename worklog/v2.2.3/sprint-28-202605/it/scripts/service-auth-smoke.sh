#!/usr/bin/env bash
#
# Sprint-28 service auth smoke
# 验证 platform 入站 filter 强校验路径:happy / 缺 token / 错 token / 未知 service
#
# 用法:
#   PLATFORM_BASE=http://dts-platform:8081 \
#   DATA_SOURCE_ID=<uuid> \
#   INGESTION_TOKEN=<expected token> \
#   ./service-auth-smoke.sh
#
# 退出码:0 全过,1 任一场景未达预期。
set -u
set -o pipefail

PLATFORM_BASE="${PLATFORM_BASE:-http://dts-platform:8081}"
DATA_SOURCE_ID="${DATA_SOURCE_ID:?need DATA_SOURCE_ID}"
INGESTION_TOKEN="${INGESTION_TOKEN:?need INGESTION_TOKEN (matches DTS_INBOUND_FROM_INGESTION on platform)}"

URL="${PLATFORM_BASE}/api/infra/data-sources/${DATA_SOURCE_ID}/runtime-detail"

pass=0
fail=0

assert_status() {
    local label="$1" expected="$2" actual="$3"
    if [[ "${actual}" == "${expected}" ]]; then
        echo "[PASS] ${label}: HTTP ${actual} (expected ${expected})"
        pass=$((pass + 1))
    else
        echo "[FAIL] ${label}: HTTP ${actual} (expected ${expected})"
        fail=$((fail + 1))
    fi
}

curl_status() {
    curl -s -o /dev/null -w "%{http_code}" "$@"
}

# S1 — happy path:有正确 service header + 正确 token → 200
status=$(curl_status -H "X-DTS-Service: dts-ingestion" -H "X-DTS-Service-Token: ${INGESTION_TOKEN}" "${URL}")
assert_status "S1 happy path (correct service+token)" "200" "${status}"

# S2 — token missing:有 service header,无 token header → 403
status=$(curl_status -H "X-DTS-Service: dts-ingestion" "${URL}")
assert_status "S2 token missing" "403" "${status}"

# S3 — token mismatch:错 token → 403
status=$(curl_status -H "X-DTS-Service: dts-ingestion" -H "X-DTS-Service-Token: WRONG-TOKEN-1234" "${URL}")
assert_status "S3 token mismatch" "403" "${status}"

# S4 — service unknown:伪造未知 service header,任意 token → 403
status=$(curl_status -H "X-DTS-Service: dts-malicious" -H "X-DTS-Service-Token: ${INGESTION_TOKEN}" "${URL}")
assert_status "S4 service unknown" "403" "${status}"

# S5 — no service header:不带 X-DTS-Service → 401(经 oauth2 resourceServer 拦截,filter 不注入 service principal)
status=$(curl_status "${URL}")
if [[ "${status}" == "401" || "${status}" == "403" ]]; then
    echo "[PASS] S5 no service header: HTTP ${status} (expected 401 or 403)"
    pass=$((pass + 1))
else
    echo "[FAIL] S5 no service header: HTTP ${status} (expected 401 or 403)"
    fail=$((fail + 1))
fi

echo "----"
echo "smoke summary: ${pass} pass / ${fail} fail"
[[ "${fail}" -eq 0 ]]

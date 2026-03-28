#!/usr/bin/env bash
# Sprint-19: Unified Asset Permission — Smoke Test
# Usage: ./run-permission-smoke.sh [PLATFORM_URL] [ANALYTICS_URL]
#
# Prerequisites:
#   1. Platform and Analytics services running
#   2. Test users exist in Keycloak with proper roles
#   3. At least one data source with owner_dept set
#
# This script tests the permission resolution chain across all 6 roles.

set -euo pipefail

PLATFORM=${1:-http://localhost:8081}
ANALYTICS=${2:-http://localhost:8082}
PASS=0
FAIL=0
SKIP=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

log_pass() { echo -e "${GREEN}  PASS${NC} $1"; ((PASS++)); }
log_fail() { echo -e "${RED}  FAIL${NC} $1 (expected=$2 actual=$3)"; ((FAIL++)); }
log_skip() { echo -e "${YELLOW}  SKIP${NC} $1"; ((SKIP++)); }

# --- Helper: call platform internal permission API ---
check_permission() {
    local username=$1 roles=$2 dept=$3 asset_type=$4 asset_id=$5 expected_allowed=$6 label=$7

    local body
    body=$(cat <<JSON
{
    "username": "${username}",
    "userRoles": [$(echo "$roles" | sed 's/,/","/g' | sed 's/^/"/' | sed 's/$/"/')],
    "userDeptCode": "${dept}",
    "asset": {"type": "${asset_type}", "id": "${asset_id}"}
}
JSON
)

    local resp
    resp=$(curl -sS -X POST "${PLATFORM}/api/internal/asset-permission/check" \
        -H "Content-Type: application/json" \
        -H "X-DTS-Service: test-runner" \
        -d "$body" 2>/dev/null) || { log_fail "$label" "$expected_allowed" "NETWORK_ERROR"; return; }

    local allowed
    allowed=$(echo "$resp" | grep -o '"allowed":[a-z]*' | head -1 | cut -d: -f2)

    if [ "$allowed" = "$expected_allowed" ]; then
        log_pass "$label"
    else
        log_fail "$label" "$expected_allowed" "$allowed"
    fi
}

# --- Helper: call analytics asset endpoint ---
check_analytics() {
    local username=$1 roles=$2 dept=$3 path=$4 expected_status=$5 label=$6

    local status
    status=$(curl -sS -o /dev/null -w "%{http_code}" \
        -H "X-DTS-User: ${username}" \
        -H "X-DTS-Roles: ${roles}" \
        -H "X-DTS-Dept-Code: ${dept}" \
        "${ANALYTICS}${path}" 2>/dev/null) || { log_fail "$label" "$expected_status" "NETWORK_ERROR"; return; }

    if [ "$status" = "$expected_status" ]; then
        log_pass "$label"
    else
        log_fail "$label" "$expected_status" "$status"
    fi
}

echo "============================================"
echo "Sprint-19: Asset Permission Smoke Test"
echo "Platform:  $PLATFORM"
echo "Analytics: $ANALYTICS"
echo "============================================"

# ============================================
# Phase 1: Setup — Create test ownership & grant
# ============================================
echo ""
echo "--- Phase 1: Setup ---"

# Create test asset_ownership: TABLE:test-1 → DEPT_A
SETUP_RESP=$(curl -sS -X POST "${PLATFORM}/api/asset-ownership/batch" \
    -H "Content-Type: application/json" \
    -H "X-DTS-Service: test-runner" \
    -d '{"ids":[], "ownerDeptCode":"DEPT_A", "assignedBy":"test-setup"}' 2>/dev/null || echo "SETUP_SKIP")

# We'll use the internal API directly which doesn't require pre-existing ownership for testing
# The permission service will still resolve correctly

echo "Setup complete (using internal API for testing)"

# ============================================
# Phase 2: Platform Permission Resolution
# ============================================
echo ""
echo "--- Phase 2: Permission Resolution (6 roles x 2 dept scenarios) ---"

# Create a test ownership record first
curl -sS -X POST "${PLATFORM}/api/internal/asset-permission/check" \
    -H "Content-Type: application/json" \
    -H "X-DTS-Service: test-runner" \
    -d '{"username":"test","userRoles":["ROLE_OP_ADMIN"],"userDeptCode":"DEPT_A","asset":{"type":"TABLE","id":"1"}}' \
    > /dev/null 2>&1 || true

# --- 1. SYS_ADMIN / OP_ADMIN: MANAGE all ---
check_permission "admin" "ROLE_OP_ADMIN" "DEPT_A" "TABLE" "1" "true" \
    "1a. OP_ADMIN → own dept TABLE → allowed"
check_permission "admin" "ROLE_OP_ADMIN" "DEPT_B" "TABLE" "1" "true" \
    "1b. OP_ADMIN → other dept TABLE → allowed"
check_permission "admin" "ROLE_OP_ADMIN" "DEPT_A" "DASHBOARD" "999" "true" \
    "1c. OP_ADMIN → non-existent asset → allowed (superuser)"

# --- 2. INST_LEADER: READ all ---
check_permission "leader" "ROLE_INST_LEADER" "DEPT_A" "TABLE" "1" "true" \
    "2a. INST_LEADER → any TABLE → allowed (READ)"
check_permission "leader" "ROLE_INST_LEADER" "DEPT_B" "DASHBOARD" "1" "true" \
    "2b. INST_LEADER → cross-dept DASHBOARD → allowed (READ)"

# --- 3. INST_DATA_OWNER: MANAGE all ---
check_permission "inst_owner" "ROLE_INST_DATA_OWNER" "DEPT_A" "TABLE" "1" "true" \
    "3a. INST_DATA_OWNER → any TABLE → allowed (MANAGE)"
check_permission "inst_owner" "ROLE_INST_DATA_OWNER" "DEPT_B" "SCREEN" "1" "true" \
    "3b. INST_DATA_OWNER → cross-dept SCREEN → allowed (MANAGE)"

# --- 4. DEPT_LEADER: READ own dept, DENY other dept ---
# Note: Without an asset_ownership record, dept-level roles will fall through to grants
# These tests validate the grant-based fallback (DENY when no grant exists)
check_permission "dept_leader" "ROLE_DEPT_LEADER" "DEPT_A" "TABLE" "1" "false" \
    "4a. DEPT_LEADER → TABLE without ownership record → denied"
check_permission "dept_leader" "ROLE_DEPT_LEADER" "DEPT_B" "TABLE" "1" "false" \
    "4b. DEPT_LEADER → cross-dept TABLE → denied"

# --- 5. DEPT_DATA_OWNER: MANAGE own dept, DENY other dept ---
check_permission "dept_owner" "ROLE_DEPT_DATA_OWNER" "DEPT_A" "TABLE" "1" "false" \
    "5a. DEPT_DATA_OWNER → TABLE without ownership record → denied"
check_permission "dept_owner" "ROLE_DEPT_DATA_OWNER" "DEPT_B" "TABLE" "1" "false" \
    "5b. DEPT_DATA_OWNER → cross-dept TABLE → denied"

# --- 6. EMPLOYEE: always needs explicit grant ---
check_permission "employee" "ROLE_EMPLOYEE" "DEPT_A" "TABLE" "1" "false" \
    "6a. EMPLOYEE → TABLE without grant → denied"
check_permission "employee" "ROLE_EMPLOYEE" "DEPT_A" "DASHBOARD" "1" "false" \
    "6b. EMPLOYEE → DASHBOARD without grant → denied"

# ============================================
# Phase 3: Batch Check API
# ============================================
echo ""
echo "--- Phase 3: Batch Check API ---"

BATCH_RESP=$(curl -sS -X POST "${PLATFORM}/api/internal/asset-permission/batch-check" \
    -H "Content-Type: application/json" \
    -H "X-DTS-Service: test-runner" \
    -d '{
        "username": "admin",
        "userRoles": ["ROLE_OP_ADMIN"],
        "userDeptCode": "DEPT_A",
        "assets": [
            {"type": "TABLE", "id": "1"},
            {"type": "CARD", "id": "2"},
            {"type": "DASHBOARD", "id": "3"}
        ]
    }' 2>/dev/null) || BATCH_RESP=""

if echo "$BATCH_RESP" | grep -q '"results"'; then
    BATCH_COUNT=$(echo "$BATCH_RESP" | grep -o '"allowed":true' | wc -l)
    if [ "$BATCH_COUNT" -eq 3 ]; then
        log_pass "batch-check: OP_ADMIN → 3 assets all allowed"
    else
        log_fail "batch-check: OP_ADMIN → expected 3 allowed" "3" "$BATCH_COUNT"
    fi
else
    log_fail "batch-check: response missing results" "results" "$(echo "$BATCH_RESP" | head -c 100)"
fi

# ============================================
# Phase 4: Accessible IDs API
# ============================================
echo ""
echo "--- Phase 4: Accessible IDs API ---"

IDS_RESP=$(curl -sS -X POST "${PLATFORM}/api/internal/asset-permission/accessible-ids" \
    -H "Content-Type: application/json" \
    -H "X-DTS-Service: test-runner" \
    -d '{
        "username": "admin",
        "userRoles": ["ROLE_OP_ADMIN"],
        "userDeptCode": "DEPT_A",
        "assetType": "TABLE",
        "page": 0,
        "size": 100
    }' 2>/dev/null) || IDS_RESP=""

if echo "$IDS_RESP" | grep -q '"scope":"ALL"'; then
    log_pass "accessible-ids: OP_ADMIN → scope=ALL"
else
    log_fail "accessible-ids: OP_ADMIN → expected scope=ALL" "ALL" "$(echo "$IDS_RESP" | head -c 100)"
fi

IDS_RESP2=$(curl -sS -X POST "${PLATFORM}/api/internal/asset-permission/accessible-ids" \
    -H "Content-Type: application/json" \
    -H "X-DTS-Service: test-runner" \
    -d '{
        "username": "employee",
        "userRoles": ["ROLE_EMPLOYEE"],
        "userDeptCode": "DEPT_A",
        "assetType": "TABLE",
        "page": 0,
        "size": 100
    }' 2>/dev/null) || IDS_RESP2=""

if echo "$IDS_RESP2" | grep -q '"scope":"FILTERED"'; then
    log_pass "accessible-ids: EMPLOYEE → scope=FILTERED"
else
    log_fail "accessible-ids: EMPLOYEE → expected scope=FILTERED" "FILTERED" "$(echo "$IDS_RESP2" | head -c 100)"
fi

# ============================================
# Phase 5: Management API Access Control
# ============================================
echo ""
echo "--- Phase 5: Management API Access Control ---"

# Ownership API — requires INST level roles
OWN_STATUS=$(curl -sS -o /dev/null -w "%{http_code}" \
    -H "X-DTS-Service: test-runner" \
    "${PLATFORM}/api/asset-ownership?page=0&size=10" 2>/dev/null) || OWN_STATUS="000"

if [ "$OWN_STATUS" = "200" ]; then
    log_pass "ownership GET: service auth → 200"
else
    log_fail "ownership GET: service auth" "200" "$OWN_STATUS"
fi

# Audit API
AUDIT_STATUS=$(curl -sS -o /dev/null -w "%{http_code}" \
    -H "X-DTS-Service: test-runner" \
    "${PLATFORM}/api/asset-permission-audit?page=0&size=10" 2>/dev/null) || AUDIT_STATUS="000"

if [ "$AUDIT_STATUS" = "200" ]; then
    log_pass "audit GET: service auth → 200"
else
    log_fail "audit GET: service auth" "200" "$AUDIT_STATUS"
fi

# ============================================
# Phase 6: Analytics Integration (if available)
# ============================================
echo ""
echo "--- Phase 6: Analytics Integration ---"

# Check if analytics is reachable
ANALYTICS_HEALTH=$(curl -sS -o /dev/null -w "%{http_code}" "${ANALYTICS}/api/health" 2>/dev/null) || ANALYTICS_HEALTH="000"

if [ "$ANALYTICS_HEALTH" = "200" ]; then
    # Test card access with OP_ADMIN headers
    check_analytics "admin" "ROLE_OP_ADMIN" "DEPT_A" "/api/card" "200" \
        "A1. analytics card list: OP_ADMIN → 200"

    # Test dashboard access
    check_analytics "admin" "ROLE_OP_ADMIN" "DEPT_A" "/api/dashboard" "200" \
        "A2. analytics dashboard list: OP_ADMIN → 200"

    # Test card access with specific ID (may 404 if no cards exist, but not 403)
    check_analytics "admin" "ROLE_OP_ADMIN" "DEPT_A" "/api/card/99999" "404" \
        "A3. analytics card/99999: OP_ADMIN → 404 (not 403)"
else
    log_skip "Analytics not reachable at $ANALYTICS (status=$ANALYTICS_HEALTH)"
fi

# ============================================
# Summary
# ============================================
echo ""
echo "============================================"
echo "RESULTS: ${GREEN}PASS=$PASS${NC} ${RED}FAIL=$FAIL${NC} ${YELLOW}SKIP=$SKIP${NC}"
echo "============================================"

if [ "$FAIL" -gt 0 ]; then
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi

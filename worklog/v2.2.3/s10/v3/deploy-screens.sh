#!/bin/bash
# ═══════════════════════════════════════════════════════════════
# deploy-screens.sh — 部署大屏实例到远程环境
#
# 用法:
#   bash deploy-screens.sh <analytics-api-base-url> <session-cookie>
#
# 示例:
#   bash deploy-screens.sh https://bi.dts.local/bi/api "metabase.SESSION=xxxxxxxx"
#
# 功能:
#   1. 自动查询远程环境的 databaseId
#   2. 替换 JSON 模板中的 {{DATABASE_ID}} 占位符
#   3. 设置变量默认值（dateFrom/dateTo）
#   4. 通过 API 创建或更新大屏
# ═══════════════════════════════════════════════════════════════
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTANCES_DIR="$SCRIPT_DIR/screen-instances"

API_BASE="${1:?用法: bash deploy-screens.sh <analytics-api-base-url> <session-cookie>}"
SESSION_COOKIE="${2:?请提供 session cookie，例: metabase.SESSION=xxxxxxxx}"

echo "=== 查询远程数据库 ID ==="
DB_LIST=$(curl -sk -H "Cookie: $SESSION_COOKIE" "$API_BASE/database" 2>/dev/null)
DB_ID=$(echo "$DB_LIST" | python3 -c "
import sys, json
try:
    data = json.loads(sys.stdin.read())
    dbs = data if isinstance(data, list) else data.get('data', data.get('databases', []))
    for db in dbs:
        if isinstance(db, dict):
            details = db.get('details_json', db.get('detailsJson', ''))
            if 'data-lake' in str(details) or 'biadmin' in str(db.get('name', '')):
                print(db.get('id', ''))
                break
    else:
        # fallback: use first database
        if dbs:
            print(dbs[0].get('id', ''))
except:
    pass
" 2>/dev/null)

if [ -z "$DB_ID" ]; then
    echo "[ERROR] 无法获取远程数据库 ID，请检查 API 地址和 Cookie"
    echo "  尝试手动指定: DATABASE_ID=<id> bash $0 $*"
    echo "  或通过 API 查询: curl -H 'Cookie: ...' $API_BASE/database"
    exit 1
fi

# Allow manual override
DB_ID="${DATABASE_ID:-$DB_ID}"
echo "  使用 databaseId: $DB_ID"

echo ""
echo "=== 部署大屏实例 ==="

for json_file in "$INSTANCES_DIR"/*.json; do
    [ -f "$json_file" ] || continue
    FILENAME=$(basename "$json_file")
    echo ""
    echo "--- 处理: $FILENAME ---"

    # Read and replace placeholder
    SCREEN_JSON=$(python3 -c "
import json, sys

with open('$json_file') as f:
    data = json.load(f)

# Replace DATABASE_ID in all components
raw = json.dumps(data, ensure_ascii=False)
raw = raw.replace('{{DATABASE_ID}}', '$DB_ID')
data = json.loads(raw)

# Set default variable values
for v in data.get('globalVariables', []):
    if v.get('key') == 'dateFrom' and not v.get('defaultValue'):
        v['defaultValue'] = '2025-01-01'
    elif v.get('key') == 'dateTo' and not v.get('defaultValue'):
        v['defaultValue'] = '2026-12-31'

print(json.dumps(data, ensure_ascii=False))
" 2>/dev/null)

    if [ -z "$SCREEN_JSON" ]; then
        echo "  [SKIP] JSON 解析失败"
        continue
    fi

    SCREEN_NAME=$(echo "$SCREEN_JSON" | python3 -c "import sys,json; print(json.loads(sys.stdin.read()).get('name',''))" 2>/dev/null)
    echo "  大屏名称: $SCREEN_NAME"

    # Check if screen already exists
    EXISTING_ID=$(curl -sk -H "Cookie: $SESSION_COOKIE" "$API_BASE/screen" 2>/dev/null | python3 -c "
import sys, json
try:
    data = json.loads(sys.stdin.read())
    screens = data if isinstance(data, list) else data.get('data', [])
    for s in screens:
        if s.get('name') == '$SCREEN_NAME':
            print(s.get('id', ''))
            break
except:
    pass
" 2>/dev/null)

    if [ -n "$EXISTING_ID" ]; then
        echo "  已存在 (id=$EXISTING_ID)，执行更新..."
        HTTP_CODE=$(curl -sk -o /dev/null -w "%{http_code}" \
            -X PUT \
            -H "Cookie: $SESSION_COOKIE" \
            -H "Content-Type: application/json" \
            -d "$SCREEN_JSON" \
            "$API_BASE/screen/$EXISTING_ID")
        echo "  更新结果: HTTP $HTTP_CODE"
    else
        echo "  不存在，执行创建..."
        HTTP_CODE=$(curl -sk -o /dev/null -w "%{http_code}" \
            -X POST \
            -H "Cookie: $SESSION_COOKIE" \
            -H "Content-Type: application/json" \
            -d "$SCREEN_JSON" \
            "$API_BASE/screen")
        echo "  创建结果: HTTP $HTTP_CODE"
    fi
done

echo ""
echo "=== 部署完成 ==="

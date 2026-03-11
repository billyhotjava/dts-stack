#!/usr/bin/env bash
# =============================================================================
# Sprint-5 部署脚本：项目主体域数据建模
#
# 功能：
#   1. 通过 API 查找或创建 ModelingPlan（项目空间）
#      - 如果同名项目已存在，自动追加后缀（name-2, name-3, ...）
#   2. 调用 dts-dbt-import 批量导入 11 个 dbt 模型
#
# 用法：
#   export API_BASE="https://bi.example.com"
#   export TOKEN="<bearer-token>"
#   export SOURCE_DATA_SOURCE_ID="<数据湖连接 UUID>"
#   bash bin/project-progress/deploy.sh [--plan-name "项目进度分析"] [--dry-run]
#
# 环境变量：
#   API_BASE                 平台地址（必填）
#   TOKEN                    访问令牌（必填）
#   SOURCE_DATA_SOURCE_ID    数据湖数据源 UUID（必填）
#   ACTIVE_DEPT              X-Active-Dept 头（可选）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MANIFEST_PATH="${SCRIPT_DIR}/manifest/models.tsv"

# 默认值
PLAN_NAME="项目进度分析"
PLAN_DOMAIN="project-management"
PLAN_SCOPE="项目主体域数据建模与指标分析"
DRY_RUN=false
INSECURE_FLAG=""

API_BASE="${API_BASE:-}"
TOKEN="${TOKEN:-}"
SOURCE_DATA_SOURCE_ID="${SOURCE_DATA_SOURCE_ID:-}"
ACTIVE_DEPT="${ACTIVE_DEPT:-}"

# ---- 辅助函数 ----
red()    { printf '\033[0;31m%s\033[0m\n' "$*"; }
green()  { printf '\033[0;32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }
info()   { echo "[deploy] $*"; }
warn()   { yellow "[deploy] WARNING: $*"; }
die()    { red "[deploy] ERROR: $*" >&2; exit 1; }

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Options:
  --plan-name <name>    项目空间名称 (default: "$PLAN_NAME")
  --insecure            忽略 TLS 证书错误
  --dry-run             仅检查，不执行
  -h, --help            显示帮助

Required env vars:
  API_BASE              平台地址
  TOKEN                 访问令牌
  SOURCE_DATA_SOURCE_ID 数据湖数据源 UUID
EOF
  exit 0
}

# ---- 参数解析 ----
while [[ $# -gt 0 ]]; do
  case "$1" in
    --plan-name)  PLAN_NAME="$2"; shift 2 ;;
    --insecure)   INSECURE_FLAG="-k"; shift ;;
    --dry-run)    DRY_RUN=true; shift ;;
    -h|--help)    usage ;;
    *)            die "unknown option: $1" ;;
  esac
done

[[ -n "$API_BASE" ]]                || die "API_BASE is required"
[[ -n "$TOKEN" ]]                   || die "TOKEN is required"
[[ -n "$SOURCE_DATA_SOURCE_ID" ]]   || die "SOURCE_DATA_SOURCE_ID is required"
[[ -f "$MANIFEST_PATH" ]]           || die "manifest not found: $MANIFEST_PATH"

API_URL="${API_BASE%/}/api"
AUTH_HEADER="Authorization: Bearer $TOKEN"
DEPT_HEADER=""
if [[ -n "$ACTIVE_DEPT" ]]; then
  DEPT_HEADER="X-Active-Dept: $ACTIVE_DEPT"
fi

curl_get() {
  local url="$1"
  local args=(-sS -H "$AUTH_HEADER" $INSECURE_FLAG)
  if [[ -n "$DEPT_HEADER" ]]; then
    args+=(-H "$DEPT_HEADER")
  fi
  curl "${args[@]}" "$url"
}

curl_post_json() {
  local url="$1"
  local data="$2"
  local args=(-sS -X POST -H "$AUTH_HEADER" -H "Content-Type: application/json" $INSECURE_FLAG)
  if [[ -n "$DEPT_HEADER" ]]; then
    args+=(-H "$DEPT_HEADER")
  fi
  curl "${args[@]}" -d "$data" "$url"
}

# ---- Step 1: 查找或创建项目空间 ----
info "=== Step 1: 查找或创建项目空间 ==="
info "目标名称: $PLAN_NAME"

find_plan_by_name() {
  local name="$1"
  local response
  response="$(curl_get "${API_URL}/modeling/plans?keyword=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$name'))")")"

  if command -v jq >/dev/null 2>&1; then
    echo "$response" | jq -r --arg name "$name" \
      '(.data // .) | if type == "array" then . else [.] end | map(select(.name == $name)) | .[0].id // empty'
  else
    echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
items = data.get('data', data) if isinstance(data, dict) else data
if isinstance(items, list):
    for item in items:
        if item.get('name') == '$name':
            print(item['id'])
            break
" 2>/dev/null || true
  fi
}

resolve_unique_plan_name() {
  local base_name="$1"
  local candidate="$base_name"
  local suffix=1

  while true; do
    local existing_id
    existing_id="$(find_plan_by_name "$candidate")"
    if [[ -z "$existing_id" ]]; then
      echo "$candidate"
      return 0
    fi
    suffix=$((suffix + 1))
    candidate="${base_name}-${suffix}"
    info "  名称 '${candidate%%-*}' 已存在 (id=$existing_id)，尝试 '$candidate'"
    if [[ $suffix -gt 99 ]]; then
      die "too many name collisions for '$base_name'"
    fi
  done
}

create_plan() {
  local name="$1"
  local response
  response="$(curl_post_json "${API_URL}/modeling/plans" "$(cat <<EOJSON
{
  "name": "$name",
  "domain": "$PLAN_DOMAIN",
  "scope": "$PLAN_SCOPE",
  "status": "DRAFT",
  "tags": "project-management,sprint-5",
  "content": "Sprint-5 项目主体域数据建模，包含 11 个 dbt 模型覆盖 35 个指标"
}
EOJSON
)")"

  local plan_id
  if command -v jq >/dev/null 2>&1; then
    plan_id="$(echo "$response" | jq -r '.id // .data.id // empty')"
  else
    plan_id="$(echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(data.get('id') or data.get('data', {}).get('id', ''))
" 2>/dev/null || true)"
  fi

  if [[ -z "$plan_id" ]]; then
    die "failed to create plan. Response: $response"
  fi
  echo "$plan_id"
}

# 先查找同名项目，有冲突则自动追加后缀
FINAL_PLAN_NAME="$(resolve_unique_plan_name "$PLAN_NAME")"

if [[ "$DRY_RUN" == true ]]; then
  info "DRY-RUN: would create plan '$FINAL_PLAN_NAME'"
  PLAN_ID="00000000-0000-0000-0000-000000000000"
else
  info "创建项目空间: $FINAL_PLAN_NAME"
  PLAN_ID="$(create_plan "$FINAL_PLAN_NAME")"
  green "[deploy] 项目空间已创建: name=$FINAL_PLAN_NAME id=$PLAN_ID"
fi

# ---- Step 2: 导入 dbt 模型 ----
info ""
info "=== Step 2: 导入 dbt 模型 ==="
info "plan_id: $PLAN_ID"
info "manifest: $MANIFEST_PATH"
info "source_data_source_id: $SOURCE_DATA_SOURCE_ID"

IMPORT_ARGS=(
  --manifest "$MANIFEST_PATH"
  --api-base "$API_BASE"
  --token "$TOKEN"
  --plan-id "$PLAN_ID"
  --source-data-source-id "$SOURCE_DATA_SOURCE_ID"
  --skip-existing
  --show-response
)

if [[ "$DRY_RUN" == true ]]; then
  IMPORT_ARGS+=(--dry-run)
fi
if [[ -n "$INSECURE_FLAG" ]]; then
  IMPORT_ARGS+=(--insecure)
fi
if [[ -n "$ACTIVE_DEPT" ]]; then
  IMPORT_ARGS+=(--active-dept "$ACTIVE_DEPT")
fi

"${PROJECT_ROOT}/bin/dts-dbt-import" "${IMPORT_ARGS[@]}"

# ---- 结果 ----
echo ""
green "============================================"
green " 部署完成"
green " 项目名称: $FINAL_PLAN_NAME"
green " 项目 ID:  $PLAN_ID"
green " 模型数量: 11"
green "============================================"
echo ""
info "后续操作："
info "  1. 在平台「逻辑建模」页面确认模型已导入"
info "  2. 触发 dbt run --select tag:project-management"
info "  3. 检查 ADS 表数据是否正确"
info "  4. 导入大屏模板: bin/project-progress/screen-template-project-progress.json"

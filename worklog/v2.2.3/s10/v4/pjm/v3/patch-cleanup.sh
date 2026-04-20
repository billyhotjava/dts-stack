#!/bin/bash
# ═══════════════════════════════════════════════════════════════
# patch-cleanup.sh — 部署 v2 模型前清理 dbt 项目中的重复 schema 定义
#
# 用法: 在目标服务器上 dbt 项目根目录下执行：
#   bash patch-cleanup.sh /opt/prod/s10/s10-stack/services/dts-dbt
#
# 说明:
#   pm_schema.yml 和 project_cockpit_schema.yml 存在 9 个重复的 model 定义。
#   本脚本删除 pm_schema.yml 中与 project_cockpit_schema.yml 重复的条目，
#   保留质量/技术状态/风险/物料/进度等独有模型。
# ═══════════════════════════════════════════════════════════════
set -euo pipefail

DBT_DIR="${1:?用法: bash patch-cleanup.sh <dbt项目根目录>}"
PM_SCHEMA="$DBT_DIR/models/pm_schema.yml"

if [ ! -f "$PM_SCHEMA" ]; then
    echo "[INFO] pm_schema.yml 不存在，无需清理"
    exit 0
fi

# 需要从 pm_schema.yml 中移除的 model（已在 project_cockpit_schema.yml 中定义）
DUPLICATES=(
    "pm_dim_major_project"
    "pm_dim_subproject"
    "pm_dim_delay_reason"
    "pm_map_node_subject"
    "biz_dwd_project_node_enriched"
    "biz_dws_week_subproject_summary"
    "biz_ads_major_project_overview"
    "biz_ads_major_project_tree_snapshot"
    "biz_ads_delay_reason_trend"
)

echo "[INFO] 检查 pm_schema.yml 中的重复定义..."

FOUND=0
for model in "${DUPLICATES[@]}"; do
    if grep -q "name: $model" "$PM_SCHEMA" 2>/dev/null; then
        FOUND=$((FOUND + 1))
        echo "  [!] 发现重复: $model"
    fi
done

if [ "$FOUND" -eq 0 ]; then
    echo "[INFO] 未发现重复定义，无需清理"
    exit 0
fi

echo "[INFO] 备份 pm_schema.yml -> pm_schema.yml.bak"
cp "$PM_SCHEMA" "$PM_SCHEMA.bak"

# 使用 Python 解析 YAML 并移除重复的 model 条目
python3 - "$PM_SCHEMA" "${DUPLICATES[@]}" <<'PYTHON'
import sys, yaml

schema_file = sys.argv[1]
to_remove = set(sys.argv[2:])

with open(schema_file, 'r', encoding='utf-8') as f:
    data = yaml.safe_load(f)

if not data or 'models' not in data:
    print("[INFO] pm_schema.yml 无 models 节点")
    sys.exit(0)

before = len(data['models'])
data['models'] = [m for m in data['models'] if m.get('name') not in to_remove]
after = len(data['models'])
removed = before - after

with open(schema_file, 'w', encoding='utf-8') as f:
    yaml.dump(data, f, default_flow_style=False, allow_unicode=True, sort_keys=False)

print(f"[INFO] 已移除 {removed} 个重复 model 定义（{before} -> {after}）")
PYTHON

echo "[OK] 清理完成，可继续部署 v2 模型"

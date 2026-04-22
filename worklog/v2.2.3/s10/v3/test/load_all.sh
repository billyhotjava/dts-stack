#!/bin/bash
# ============================================================
# 一键加载全部 ODS v2 测试数据
# 用法: bash load_all.sh
# 前提: ods_create_tables_v2.sql 已执行
# ============================================================

set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
CONTAINER="s10-stack_dts-pg_1"
DB_USER="biadmin"
DB_NAME="biadmin"

echo "=== 加载 ODS v2 测试数据 ==="

for f in "$DIR"/0*.sql; do
    fname=$(basename "$f")
    echo "  -> $fname"
    docker cp "$f" "$CONTAINER":/tmp/
    docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -f "/tmp/$fname"
done

echo ""
echo "=== 验证行数 ==="
docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "
SELECT t AS table_name, c AS row_count FROM (
    SELECT 'ods_project_subject_domain_v2' AS t, count(*) AS c FROM ods_project_subject_domain_v2
    UNION ALL SELECT 'ods_progress_measure_v2',     count(*) FROM ods_progress_measure_v2
    UNION ALL SELECT 'ods_quality_issue_v2',         count(*) FROM ods_quality_issue_v2
    UNION ALL SELECT 'ods_quality_measure_v2',       count(*) FROM ods_quality_measure_v2
    UNION ALL SELECT 'ods_tech_state_v2',            count(*) FROM ods_tech_state_v2
    UNION ALL SELECT 'ods_tech_state_measure_v2',    count(*) FROM ods_tech_state_measure_v2
    UNION ALL SELECT 'ods_risk_info_v2',             count(*) FROM ods_risk_info_v2
    UNION ALL SELECT 'ods_risk_measure_v2',          count(*) FROM ods_risk_measure_v2
    UNION ALL SELECT 'ods_material_info_v2',         count(*) FROM ods_material_info_v2
) sub ORDER BY t;
"

echo ""
echo "=== 完成 ==="

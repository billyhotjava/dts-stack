{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 风险信息汇总表
-- 入湖目标表: ods_risk_info
-- 字段数: 14 (不含 id/source_system/import_time)
-- 用途: 风险事实

SELECT * FROM ods_risk_info

{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 风险跟进措施表
-- 入湖目标表: ods_risk_measure
-- 字段数: 11 (不含 id/source_system/import_time)
-- 用途: 风险闭环措施

SELECT * FROM ods_risk_measure

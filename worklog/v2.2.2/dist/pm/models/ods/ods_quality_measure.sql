{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 质量跟进措施表
-- 入湖目标表: ods_quality_measure
-- 字段数: 10 (不含 id/source_system/import_time)
-- 用途: 质量闭环措施

SELECT * FROM ods_quality_measure

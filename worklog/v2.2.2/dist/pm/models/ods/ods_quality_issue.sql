{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 质量信息汇总表
-- 入湖目标表: ods_quality_issue
-- 字段数: 13 (不含 id/source_system/import_time)
-- 用途: 质量问题事实

SELECT * FROM ods_quality_issue

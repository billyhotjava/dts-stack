{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 技术状态信息汇总表
-- 入湖目标表: ods_tech_state
-- 字段数: 14 (不含 id/source_system/import_time)
-- 用途: 技术状态变更事实

SELECT * FROM ods_tech_state

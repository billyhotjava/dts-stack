{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 技术状态跟进措施表
-- 入湖目标表: ods_tech_state_measure
-- 字段数: 10 (不含 id/source_system/import_time)
-- 用途: 技术状态闭环措施

SELECT * FROM ods_tech_state_measure

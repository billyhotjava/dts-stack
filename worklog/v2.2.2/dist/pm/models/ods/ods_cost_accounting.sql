{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 成本核算基本表
-- 入湖目标表: ods_cost_accounting
-- 字段数: 8 (不含 id/source_system/import_time)
-- 用途: 成本事实

SELECT * FROM ods_cost_accounting

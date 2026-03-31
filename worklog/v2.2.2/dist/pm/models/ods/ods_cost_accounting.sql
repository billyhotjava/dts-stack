{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 成本核算基本表
-- 入湖目标表: ods_cost_accounting
-- 字段数: 8 (不含 id/source_system/import_time)

SELECT
  CAST(NULL AS varchar(500)) AS project_no,
  CAST(NULL AS varchar(500)) AS project_name,
  CAST(NULL AS varchar(500)) AS accounting_period,
  CAST(NULL AS varchar(500)) AS budget_amount,
  CAST(NULL AS varchar(500)) AS actual_amount,
  CAST(NULL AS varchar(500)) AS dept,
  CAST(NULL AS varchar(500)) AS cost_category,
  CAST(NULL AS varchar(2000)) AS remark
WHERE FALSE

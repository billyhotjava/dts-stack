{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 质量信息汇总表
-- 入湖目标表: ods_quality_issue
-- 字段数: 13 (不含 id/source_system/import_time)

SELECT
  CAST(NULL AS varchar(500)) AS project_no,
  CAST(NULL AS varchar(2000)) AS issue_name,
  CAST(NULL AS varchar(500)) AS issue_category,
  CAST(NULL AS varchar(500)) AS issue_date,
  CAST(NULL AS varchar(500)) AS status,
  CAST(NULL AS varchar(500)) AS closure_status,
  CAST(NULL AS varchar(500)) AS zero_plan,
  CAST(NULL AS varchar(500)) AS dept,
  CAST(NULL AS varchar(500)) AS subsystem,
  CAST(NULL AS varchar(500)) AS owner,
  CAST(NULL AS varchar(500)) AS last_update_time,
  CAST(NULL AS varchar(500)) AS filled_by,
  CAST(NULL AS varchar(2000)) AS remark
WHERE FALSE

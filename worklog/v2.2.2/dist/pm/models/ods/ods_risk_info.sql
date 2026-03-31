{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 风险信息汇总表
-- 入湖目标表: ods_risk_info
-- 字段数: 14 (不含 id/source_system/import_time)

SELECT
  CAST(NULL AS varchar(500)) AS project_no,
  CAST(NULL AS varchar(2000)) AS risk_name,
  CAST(NULL AS varchar(500)) AS risk_level,
  CAST(NULL AS varchar(500)) AS risk_submit_time,
  CAST(NULL AS varchar(2000)) AS risk_content,
  CAST(NULL AS varchar(500)) AS impact_scope,
  CAST(NULL AS varchar(500)) AS closure_status,
  CAST(NULL AS varchar(2000)) AS response_measure,
  CAST(NULL AS varchar(500)) AS dept,
  CAST(NULL AS varchar(500)) AS subsystem,
  CAST(NULL AS varchar(500)) AS owner,
  CAST(NULL AS varchar(500)) AS last_update_time,
  CAST(NULL AS varchar(500)) AS filled_by,
  CAST(NULL AS varchar(2000)) AS remark
WHERE FALSE

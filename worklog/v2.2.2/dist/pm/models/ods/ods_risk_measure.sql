{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 风险跟进措施表
-- 入湖目标表: ods_risk_measure
-- 字段数: 11 (不含 id/source_system/import_time)

SELECT
  CAST(NULL AS varchar(500)) AS project_no,
  CAST(NULL AS varchar(2000)) AS risk_name,
  CAST(NULL AS varchar(2000)) AS measure_content,
  CAST(NULL AS varchar(500)) AS measure_status,
  CAST(NULL AS varchar(500)) AS responsible_person,
  CAST(NULL AS varchar(500)) AS deadline,
  CAST(NULL AS varchar(500)) AS actual_complete_date,
  CAST(NULL AS varchar(2000)) AS closure_deliverable,
  CAST(NULL AS varchar(500)) AS last_update_time,
  CAST(NULL AS varchar(500)) AS filled_by,
  CAST(NULL AS varchar(2000)) AS remark
WHERE FALSE

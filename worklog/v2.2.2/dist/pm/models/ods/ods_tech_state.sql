{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 技术状态信息汇总表
-- 入湖目标表: ods_tech_state
-- 字段数: 14 (不含 id/source_system/import_time)

SELECT
  CAST(NULL AS varchar(500)) AS project_no,
  CAST(NULL AS varchar(2000)) AS tech_state_name,
  CAST(NULL AS varchar(2000)) AS change_item,
  CAST(NULL AS varchar(500)) AS change_category,
  CAST(NULL AS varchar(500)) AS change_submit_time,
  CAST(NULL AS varchar(500)) AS file_signature_status,
  CAST(NULL AS varchar(500)) AS completion_signature,
  CAST(NULL AS varchar(500)) AS reform_status,
  CAST(NULL AS varchar(500)) AS closure_status,
  CAST(NULL AS varchar(500)) AS dept,
  CAST(NULL AS varchar(500)) AS subsystem,
  CAST(NULL AS varchar(500)) AS last_update_time,
  CAST(NULL AS varchar(500)) AS filled_by,
  CAST(NULL AS varchar(2000)) AS remark
WHERE FALSE

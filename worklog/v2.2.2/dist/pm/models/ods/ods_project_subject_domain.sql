{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 项目主体域原始数据（执行域）
-- 入湖目标表: ods_project_subject_domain
-- 字段数: 31 (不含 id/source_system/import_time)

SELECT
  CAST(NULL AS varchar(500)) AS project_no,
  CAST(NULL AS varchar(500)) AS subsystem,
  CAST(NULL AS varchar(500)) AS node_task,
  CAST(NULL AS varchar(500)) AS plan_date,
  CAST(NULL AS varchar(500)) AS plan_week,
  CAST(NULL AS varchar(500)) AS deliverable,
  CAST(NULL AS varchar(500)) AS node_type,
  CAST(NULL AS varchar(500)) AS owner,
  CAST(NULL AS varchar(500)) AS dept,
  CAST(NULL AS varchar(500)) AS dept_leader,
  CAST(NULL AS varchar(500)) AS completion_status,
  CAST(NULL AS varchar(500)) AS collab_dept,
  CAST(NULL AS varchar(500)) AS supervisor_dept,
  CAST(NULL AS varchar(500)) AS delay_expected_date,
  CAST(NULL AS varchar(500)) AS incomplete_reason,
  CAST(NULL AS varchar(500)) AS risk_level,
  CAST(NULL AS varchar(500)) AS risk_content,
  CAST(NULL AS varchar(500)) AS delay_impact,
  CAST(NULL AS varchar(500)) AS actual_date,
  CAST(NULL AS varchar(500)) AS actual_week,
  CAST(NULL AS varchar(500)) AS institute_leader,
  CAST(NULL AS varchar(500)) AS source,
  CAST(NULL AS varchar(500)) AS original_plan_date,
  CAST(NULL AS varchar(500)) AS delay_days_changed,
  CAST(NULL AS varchar(500)) AS delay_days_unchanged,
  CAST(NULL AS varchar(500)) AS delay_applied,
  CAST(NULL AS varchar(500)) AS project_manager,
  CAST(NULL AS varchar(500)) AS last_update_time,
  CAST(NULL AS varchar(500)) AS last_update_week,
  CAST(NULL AS varchar(500)) AS filled_by,
  CAST(NULL AS varchar(500)) AS highlight
WHERE FALSE

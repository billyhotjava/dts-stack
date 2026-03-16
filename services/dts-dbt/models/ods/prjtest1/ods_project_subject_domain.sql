{{ config(materialized='view', tags=['project-management', 'biz', 'ods']) }}

SELECT
  project_no,
  subsystem,
  node_task,
  plan_date,
  plan_week,
  node_type,
  owner,
  dept,
  dept_leader,
  completion_status,
  collab_dept,
  supervisor_dept,
  delay_expected_date,
  incomplete_reason,
  risk_level,
  risk_content,
  delay_impact,
  actual_date,
  actual_week,
  institute_leader,
  source,
  original_plan_date,
  delay_days_changed,
  delay_days_unchanged,
  delay_applied,
  project_manager,
  last_update_time,
  filled_by,
  highlight
FROM {{ source('pm_ods', 'project_subject_domain') }}
WHERE COALESCE(btrim(project_no), '') <> ''

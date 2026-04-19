{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'quality']) }}

WITH typed AS (
  SELECT
    s.*,
    COALESCE(s.last_update_time, current_date) AS state_as_of_date,
    EXTRACT(YEAR FROM s.issue_date)::int AS issue_year,
    EXTRACT(QUARTER FROM s.issue_date)::int AS issue_quarter
  FROM {{ ref('stg_pm__quality_issue_v2') }} s
  WHERE s.project_no IS NOT NULL
    AND s.issue_date IS NOT NULL
)

SELECT
  concat('quality_issue:', t.source_row_id) AS issue_id,

  t.source_row_id,
  t.source_table,
  t.source_system,
  t.source_file,
  t.source_sheet_name,
  t.source_batch_id,
  t.source_row_num,
  t.imported_at AS source_imported_at,

  t.project_no,
  t.subsystem,
  t.issue_name,
  t.dept,
  t.team_leader,
  t.dept_leader,
  t.issue_summary,

  t.issue_category_raw,
  t.issue_category,
  qc.quality_category_id,
  t.zero_plan,
  t.zero_plan_synced_raw,
  t.zero_plan_synced,
  t.status_raw,
  t.status,
  qs.quality_status_id,
  t.current_progress,
  t.project_manager,
  t.filled_by,

  COALESCE(qs.is_zero_completed, false) AS is_zero_completed,
  COALESCE(qs.is_tech_zero, false) AS is_tech_zero,
  COALESCE(qs.is_mgmt_zero, false) AS is_mgmt_zero,
  COALESCE(qs.is_both_zero, false) AS is_both_zero,

  COALESCE(qc.cat_design, false) AS cat_design,
  COALESCE(qc.cat_process, false) AS cat_process,
  COALESCE(qc.cat_management, false) AS cat_management,
  COALESCE(qc.cat_component, false) AS cat_component,
  COALESCE(qc.cat_operation, false) AS cat_operation,
  COALESCE(qc.cat_outsource, false) AS cat_outsource,
  COALESCE(qc.cat_software, false) AS cat_software,
  COALESCE(qc.cat_environment, false) AS cat_environment,
  CASE
    WHEN qc.code IS NULL OR COALESCE(qc.cat_other, false) THEN true
    ELSE false
  END AS cat_other,

  t.new_plan_count,

  t.issue_date_raw,
  t.issue_date,
  t.zero_complete_date,
  t.last_update_time,

  t.issue_week,
  t.zero_complete_week,
  t.last_update_week,

  t.has_zero_plan,

  t.issue_year,
  t.issue_quarter,
  t.issue_month,
  t.zero_complete_month,
  t.last_update_month,
  t.state_as_of_date,
  to_char(t.state_as_of_date, 'YYYY-MM') AS state_as_of_month,

  CASE
    WHEN COALESCE(qs.is_zero_completed, false) = false
    THEN (t.state_as_of_date - t.issue_date)::int
    ELSE 0
  END AS pending_days,

  now() AS etl_time
FROM typed t
LEFT JOIN {{ ref('dim_quality_status_v2') }} qs
  ON qs.code = t.status
LEFT JOIN {{ ref('dim_quality_category_v2') }} qc
  ON qc.code = t.issue_category

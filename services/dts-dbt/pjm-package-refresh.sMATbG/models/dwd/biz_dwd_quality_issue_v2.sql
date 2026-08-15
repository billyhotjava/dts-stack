{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'quality']) }}

WITH stg AS (
  SELECT * FROM {{ ref('stg_pm__quality_issue_v2') }}
  WHERE project_no IS NOT NULL
    AND issue_date IS NOT NULL
),
normalized AS (
  SELECT
    s.*,
    qca.canonical_code AS issue_category,
    qsa.canonical_code AS status,
    ba.canonical_code  AS zero_plan_synced
  FROM stg s
  LEFT JOIN {{ ref('dim_quality_category_alias') }} qca
    ON qca.alias_raw = s.issue_category_raw
  LEFT JOIN {{ ref('dim_quality_status_alias') }} qsa
    ON qsa.alias_raw = s.status_raw
  LEFT JOIN {{ ref('dim_boolean_alias') }} ba
    ON ba.alias_raw = upper(s.zero_plan_synced_raw)
),
derived AS (
  SELECT
    n.*,
    to_char(n.issue_date, 'YYYY-MM') AS issue_month,
    to_char(n.zero_complete_date, 'YYYY-MM') AS zero_complete_month,
    to_char(n.last_update_time, 'YYYY-MM') AS last_update_month,
    EXTRACT(YEAR FROM n.issue_date)::int AS issue_year,
    EXTRACT(QUARTER FROM n.issue_date)::int AS issue_quarter,
    COALESCE(n.last_update_time, current_date) AS state_as_of_date,
    CASE
      WHEN btrim(COALESCE(n.zero_plan, '')) IN ('', '无') THEN false
      ELSE true
    END AS has_zero_plan
  FROM normalized n
)

SELECT
  concat('quality_issue:', d.source_row_id) AS issue_id,

  d.source_row_id,
  d.source_table,
  d.source_system,
  d.imported_at AS source_imported_at,

  d.project_no,
  d.subsystem,
  d.issue_name,
  d.dept,
  d.team_leader,
  d.dept_leader,
  d.issue_summary,

  d.issue_category_raw,
  d.issue_category,
  qc.quality_category_id,
  qc.label AS issue_category_label,

  d.zero_plan,
  d.zero_plan_synced_raw,
  COALESCE(d.zero_plan_synced, d.zero_plan_synced_raw) AS zero_plan_synced,

  d.status_raw,
  d.status,
  qs.quality_status_id,
  qs.label AS status_label,

  d.current_progress,
  d.project_manager,
  d.filled_by,

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

  d.new_plan_count,

  d.issue_date_raw,
  d.issue_date,
  d.zero_complete_date,
  d.last_update_time,

  d.issue_week,
  d.zero_complete_week,
  d.last_update_week,

  d.has_zero_plan,

  d.issue_year,
  d.issue_quarter,
  d.issue_month,
  d.zero_complete_month,
  d.last_update_month,
  d.state_as_of_date,
  to_char(d.state_as_of_date, 'YYYY-MM') AS state_as_of_month,

  CASE
    WHEN COALESCE(qs.is_zero_completed, false) = false
      THEN (d.state_as_of_date - d.issue_date)::int
    ELSE 0
  END AS pending_days,

  CASE
    WHEN COALESCE(qs.is_zero_completed, false) AND d.zero_complete_date IS NOT NULL
      THEN GREATEST(0, d.zero_complete_date - d.issue_date)
    ELSE GREATEST(0, d.state_as_of_date - d.issue_date)
  END AS aging_days,

  now() AS etl_time
FROM derived d
LEFT JOIN {{ ref('dim_quality_status_v2') }} qs
  ON qs.code = d.status
LEFT JOIN {{ ref('dim_quality_category_v2') }} qc
  ON qc.code = d.issue_category

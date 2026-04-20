{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd']) }}

WITH stg AS (
  SELECT * FROM {{ ref('stg_pm__project_subject_domain_v2') }}
  WHERE project_no IS NOT NULL
    AND plan_date IS NOT NULL
),
normalized AS (
  SELECT
    s.*,
    s.completion_status_raw AS completion_status,
    nta.canonical_code AS node_type,
    rla.canonical_code AS risk_level,
    ba.canonical_code AS delay_applied
  FROM stg s
  LEFT JOIN {{ ref('dim_node_type_alias') }} nta
    ON nta.alias_raw = s.node_type_raw
  LEFT JOIN {{ ref('dim_risk_level_alias') }} rla
    ON rla.alias_raw = s.risk_level_raw
  LEFT JOIN {{ ref('dim_boolean_alias') }} ba
    ON ba.alias_raw = upper(s.delay_applied_raw)
),
derived AS (
  SELECT
    n.*,
    to_char(n.plan_date, 'YYYY-MM') AS plan_month,
    to_char(n.actual_date, 'YYYY-MM') AS actual_month,
    to_char(n.last_update_time, 'YYYY-MM') AS last_update_month,
    EXTRACT(YEAR FROM n.plan_date)::int AS plan_year,
    EXTRACT(QUARTER FROM n.plan_date)::int AS plan_quarter,
    EXTRACT(YEAR FROM n.actual_date)::int AS actual_year,
    COALESCE(n.last_update_time, current_date) AS as_of_date,
    CASE
      WHEN n.actual_date IS NOT NULL
        THEN (n.actual_date - n.plan_date)::int
      WHEN n.plan_date < COALESCE(n.last_update_time, current_date)
        THEN (COALESCE(n.last_update_time, current_date) - n.plan_date)::int
    END AS delay_days,
    (n.plan_date <= COALESCE(n.last_update_time, current_date)) AS is_due
  FROM normalized n
)

SELECT
  concat('project_node:', d.source_row_id) AS node_id,

  d.source_row_id,
  d.source_table,
  d.source_system,
  d.imported_at AS source_imported_at,

  d.project_no,
  d.subsystem,
  d.node_task,
  d.owner,
  d.dept,
  d.dept_leader,
  d.collab_dept,
  d.supervisor_dept,
  d.incomplete_reason,
  d.risk_content,
  d.delay_impact,
  d.institute_leader,
  d.project_manager,
  d.filled_by,
  d.highlight,
  d.deliverable,

  d.completion_status_raw,
  d.completion_status,
  cs.completion_status_id,
  cs.label AS completion_status_label,
  COALESCE(cs.is_completed, false) AS is_completed,
  COALESCE(cs.is_on_time, false) AS is_on_time,
  COALESCE(cs.is_overdue_completed, false) AS is_overdue_completed,
  COALESCE(cs.is_incomplete, false) AS is_incomplete,
  COALESCE(cs.is_pending_normal, false) AS is_pending_normal,
  COALESCE(cs.is_abnormal_pending, false) AS is_abnormal_pending,
  COALESCE(cs.is_overdue_unchanged, false) AS is_overdue_unchanged,
  COALESCE(cs.is_overdue_changed, false) AS is_overdue_changed,
  COALESCE(cs.is_overdue_done_unchanged, false) AS is_overdue_done_unchanged,
  COALESCE(cs.is_overdue_done_changed, false) AS is_overdue_done_changed,
  COALESCE(cs.is_overdue_completed_effective, false) AS is_overdue_completed_effective,
  COALESCE(cs.is_overdue_incomplete_effective, false) AS is_overdue_incomplete_effective,

  d.node_type_raw,
  d.node_type,
  nt.node_type_id,
  nt.label AS node_type_label,
  COALESCE(nt.is_general, false) AS is_general_node,
  COALESCE(nt.is_important, false) AS is_important_node,
  COALESCE(nt.is_major, false) AS is_major_node,
  COALESCE(nt.is_milestone, false) AS is_milestone_node,

  d.risk_level_raw,
  d.risk_level,
  rl.risk_level_id,
  rl.label AS risk_level_label,
  COALESCE(rl.is_high, false) AS is_high_risk,
  COALESCE(rl.is_mid, false) AS is_mid_risk,
  COALESCE(rl.is_low, false) AS is_low_risk,

  d.data_source,
  d.delay_applied_raw,
  COALESCE(d.delay_applied, d.delay_applied_raw) AS delay_applied,

  d.plan_date_raw,
  d.plan_start_date,
  d.plan_date,
  d.actual_start_date,
  d.actual_date,
  d.delay_expected_date,
  d.original_plan_date,
  d.last_update_time,

  d.plan_week,
  d.actual_week,
  d.last_update_week,

  d.plan_year,
  d.plan_quarter,
  d.plan_month,
  d.actual_year,
  d.actual_month,
  d.last_update_month,
  d.as_of_date,
  to_char(d.as_of_date, 'YYYY-MM') AS as_of_month,

  d.delay_days,
  d.is_due,

  now() AS etl_time
FROM derived d
LEFT JOIN {{ ref('dim_completion_status_v2') }} cs
  ON cs.code = d.completion_status
LEFT JOIN {{ ref('dim_node_type_v2') }} nt
  ON nt.code = d.node_type
LEFT JOIN {{ ref('dim_risk_level_v2') }} rl
  ON rl.code = d.risk_level

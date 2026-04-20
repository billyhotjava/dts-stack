

WITH typed AS (
  SELECT
    s.*,
    COALESCE(s.last_update_time, current_date) AS as_of_date,
    EXTRACT(YEAR FROM s.plan_date)::int AS plan_year,
    EXTRACT(QUARTER FROM s.plan_date)::int AS plan_quarter,
    EXTRACT(YEAR FROM s.actual_date)::int AS actual_year,
    CASE
      WHEN s.actual_date IS NOT NULL
      THEN (s.actual_date - s.plan_date)::int
      WHEN s.plan_date < COALESCE(s.last_update_time, current_date)
      THEN (COALESCE(s.last_update_time, current_date) - s.plan_date)::int
    END AS delay_days,
    (s.plan_date <= COALESCE(s.last_update_time, current_date)) AS is_due
  FROM "biadmin"."public"."stg_pm__project_subject_domain_v2" s
  WHERE s.project_no IS NOT NULL
    AND s.plan_date IS NOT NULL
)

SELECT
  concat('project_node:', t.source_row_id) AS node_id,

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
  t.node_task,
  t.owner,
  t.dept,
  t.dept_leader,
  t.collab_dept,
  t.supervisor_dept,
  t.incomplete_reason,
  t.risk_content,
  t.delay_impact,
  t.institute_leader,
  t.project_manager,
  t.filled_by,
  t.highlight,
  t.deliverable,

  t.completion_status_raw,
  t.completion_status,
  cs.completion_status_id,
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

  t.node_type_raw,
  t.node_type,
  nt.node_type_id,
  COALESCE(nt.is_general, false) AS is_general_node,
  COALESCE(nt.is_important, false) AS is_important_node,
  COALESCE(nt.is_major, false) AS is_major_node,
  COALESCE(nt.is_milestone, false) AS is_milestone_node,

  t.risk_level_raw,
  t.risk_level,
  rl.risk_level_id,
  COALESCE(rl.is_high, false) AS is_high_risk,
  COALESCE(rl.is_mid, false) AS is_mid_risk,
  COALESCE(rl.is_low, false) AS is_low_risk,

  t.data_source,
  t.delay_applied_raw,
  t.delay_applied,

  t.plan_date_raw,
  t.plan_start_date,
  t.plan_date,
  t.actual_start_date,
  t.actual_date,
  t.delay_expected_date,
  t.original_plan_date,
  t.last_update_time,

  t.plan_week,
  t.actual_week,
  t.last_update_week,

  t.plan_year,
  t.plan_quarter,
  t.plan_month,
  t.actual_year,
  t.actual_month,
  t.last_update_month,
  t.as_of_date,
  to_char(t.as_of_date, 'YYYY-MM') AS as_of_month,

  t.delay_days,
  t.is_due,

  now() AS etl_time
FROM typed t
LEFT JOIN "biadmin"."public"."dim_completion_status_v2" cs
  ON cs.code = t.completion_status
LEFT JOIN "biadmin"."public"."dim_node_type_v2" nt
  ON nt.code = t.node_type
LEFT JOIN "biadmin"."public"."dim_risk_level_v2" rl
  ON rl.code = t.risk_level
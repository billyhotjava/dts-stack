{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd']) }}

WITH cleaned AS (
  SELECT
    {{ nullif_placeholder("o.project_no") }}             AS project_no,
    {{ nullif_placeholder("o.subsystem") }}              AS subsystem,
    {{ nullif_placeholder("o.node_task") }}              AS node_task,
    {{ nullif_placeholder("o.owner") }}                  AS owner,
    {{ nullif_placeholder("o.dept") }}                   AS dept,
    {{ nullif_placeholder("o.dept_leader") }}            AS dept_leader,
    {{ nullif_placeholder("o.collab_dept") }}            AS collab_dept,
    {{ nullif_placeholder("o.supervisor_dept") }}        AS supervisor_dept,
    {{ nullif_placeholder("o.incomplete_reason") }}      AS incomplete_reason,
    {{ nullif_placeholder("o.risk_content") }}           AS risk_content,
    {{ nullif_placeholder("o.delay_impact") }}           AS delay_impact,
    {{ nullif_placeholder("o.institute_leader") }}       AS institute_leader,
    {{ nullif_placeholder("o.project_manager") }}        AS project_manager,
    {{ nullif_placeholder("o.filled_by") }}              AS filled_by,
    {{ nullif_placeholder("o.highlight") }}              AS highlight,
    {{ nullif_placeholder("o.deliverable") }}            AS deliverable,
    {{ nullif_placeholder("o.completion_status") }}      AS completion_status,
    {{ nullif_placeholder("o.node_type") }}              AS node_type,
    {{ nullif_placeholder("o.risk_level") }}             AS risk_level,
    {{ nullif_placeholder("o.source") }}                 AS data_source,
    {{ nullif_placeholder("o.delay_applied") }}          AS delay_applied,
    {{ nullif_placeholder("o.plan_date") }}              AS plan_date_raw,
    {{ parse_date_safe("o.plan_start_date") }}           AS plan_start_date,
    {{ parse_date_safe("o.plan_date") }}                 AS plan_date,
    {{ parse_date_safe("o.actual_start_date") }}         AS actual_start_date,
    {{ parse_date_safe("o.actual_date") }}               AS actual_date,
    {{ parse_date_safe("o.delay_expected_date") }}       AS delay_expected_date,
    {{ parse_date_safe("o.original_plan_date") }}        AS original_plan_date,
    {{ parse_date_safe("o.last_update_time") }}          AS last_update_time,
    {{ parse_numeric_safe("o.plan_week") }}::int         AS plan_week,
    {{ parse_numeric_safe("o.actual_week") }}::int       AS actual_week,
    {{ parse_numeric_safe("o.last_update_week") }}::int  AS last_update_week
  FROM {{ source('pm_ods_v2', 'project_subject_domain_v2') }} o
),
typed AS (
  SELECT
    c.*,
    EXTRACT(YEAR FROM c.plan_date)::int                    AS plan_year,
    EXTRACT(QUARTER FROM c.plan_date)::int                 AS plan_quarter,
    to_char(c.plan_date, 'YYYY-MM')                        AS plan_month,
    EXTRACT(YEAR FROM c.actual_date)::int                  AS actual_year,
    to_char(c.actual_date, 'YYYY-MM')                      AS actual_month,
    CASE
      WHEN c.actual_date IS NOT NULL
      THEN (c.actual_date - c.plan_date)::int
      WHEN c.plan_date < current_date
      THEN (current_date - c.plan_date)::int
    END                                                    AS delay_days,
    (c.plan_date <= current_date)                          AS is_due
  FROM cleaned c
  WHERE c.project_no IS NOT NULL
    AND c.plan_date IS NOT NULL
)

SELECT
  -- === 主键 ===
  md5(
    COALESCE(t.project_no, '') || '|' ||
    COALESCE(t.subsystem, '') || '|' ||
    COALESCE(t.node_task, '') || '|' ||
    COALESCE(t.plan_date_raw, '')
  ) AS node_id,

  -- === 原始业务字段 ===
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

  -- === 枚举标准化 ===
  t.completion_status,
  COALESCE(cs.is_completed, false)                           AS is_completed,
  COALESCE(cs.is_on_time, false)                             AS is_on_time,
  COALESCE(cs.is_overdue_completed, false)                   AS is_overdue_completed,
  COALESCE(cs.is_incomplete, false)                          AS is_incomplete,
  COALESCE(cs.is_pending_normal, false)                      AS is_pending_normal,
  COALESCE(cs.is_abnormal_pending, false)                    AS is_abnormal_pending,
  COALESCE(cs.is_overdue_unchanged, false)                   AS is_overdue_unchanged,
  COALESCE(cs.is_overdue_changed, false)                     AS is_overdue_changed,
  COALESCE(cs.is_overdue_done_unchanged, false)              AS is_overdue_done_unchanged,
  COALESCE(cs.is_overdue_done_changed, false)                AS is_overdue_done_changed,
  COALESCE(cs.is_overdue_completed_effective, false)         AS is_overdue_completed_effective,
  COALESCE(cs.is_overdue_incomplete_effective, false)        AS is_overdue_incomplete_effective,

  t.node_type,
  COALESCE(nt.is_general, false)                             AS is_general_node,
  COALESCE(nt.is_important, false)                           AS is_important_node,
  COALESCE(nt.is_major, false)                               AS is_major_node,
  COALESCE(nt.is_milestone, false)                           AS is_milestone_node,

  t.risk_level,
  COALESCE(rl.is_high, false)                                AS is_high_risk,
  COALESCE(rl.is_mid, false)                                 AS is_mid_risk,
  COALESCE(rl.is_low, false)                                 AS is_low_risk,
  t.data_source,
  t.delay_applied,

  -- === 日期解析 ===
  t.plan_start_date,
  t.plan_date,
  t.actual_start_date,
  t.actual_date,
  t.delay_expected_date,
  t.original_plan_date,
  t.last_update_time,

  -- === 周数 ===
  t.plan_week,
  t.actual_week,
  t.last_update_week,

  -- === 时间维度标签 ===
  t.plan_year,
  t.plan_quarter,
  t.plan_month,
  t.actual_year,
  t.actual_month,

  -- === 衍生字段 ===
  t.delay_days,
  t.is_due,

  'ods_project_subject_domain_v2'::text AS source_table,
  now() AS etl_time

FROM typed t
LEFT JOIN {{ ref('dim_completion_status_v2') }} cs
  ON cs.code = t.completion_status
LEFT JOIN {{ ref('dim_node_type_v2') }} nt
  ON nt.code = t.node_type
LEFT JOIN {{ ref('dim_risk_level_v2') }} rl
  ON rl.code = t.risk_level

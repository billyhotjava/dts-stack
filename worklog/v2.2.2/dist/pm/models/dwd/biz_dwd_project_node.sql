{{ config(materialized='table', tags=['project-management', 'biz', 'dwd']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.subsystem), '') || '|' ||
    COALESCE(btrim(o.node_task), '') || '|' ||
    COALESCE(btrim(o.plan_date), '')
  ) AS node_id,

  -- === 原始业务字段 ===
  {{ nullif_placeholder("o.project_no") }}          AS project_no,
  {{ nullif_placeholder("o.subsystem") }}           AS subsystem,
  {{ nullif_placeholder("o.node_task") }}           AS node_task,
  {{ nullif_placeholder("o.owner") }}               AS owner,
  {{ nullif_placeholder("o.dept") }}                AS dept,
  {{ nullif_placeholder("o.dept_leader") }}         AS dept_leader,
  {{ nullif_placeholder("o.collab_dept") }}         AS collab_dept,
  {{ nullif_placeholder("o.supervisor_dept") }}     AS supervisor_dept,
  {{ nullif_placeholder("o.incomplete_reason") }}   AS incomplete_reason,
  {{ nullif_placeholder("o.risk_content") }}        AS risk_content,
  {{ nullif_placeholder("o.delay_impact") }}        AS delay_impact,
  {{ nullif_placeholder("o.institute_leader") }}    AS institute_leader,
  {{ nullif_placeholder("o.project_manager") }}     AS project_manager,
  {{ nullif_placeholder("o.filled_by") }}           AS filled_by,
  {{ nullif_placeholder("o.highlight") }}           AS highlight,
  {{ nullif_placeholder("o.deliverable") }}          AS deliverable,
  {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week,

  -- === 枚举标准化 ===
  {{ nullif_placeholder("o.completion_status") }}   AS completion_status,
  COALESCE(cs.is_completed, false)         AS is_completed,
  COALESCE(cs.is_on_time, false)           AS is_on_time,
  COALESCE(cs.is_overdue_completed, false) AS is_overdue_completed,
  COALESCE(cs.is_incomplete, false)        AS is_incomplete,

  {{ nullif_placeholder("o.node_type") }}           AS node_type,
  COALESCE(nt.is_general, false)           AS is_general_node,

  {{ nullif_placeholder("o.risk_level") }}          AS risk_level,

  {{ nullif_placeholder("o.source") }}              AS data_source,
  {{ nullif_placeholder("o.delay_applied") }}       AS delay_applied,

  -- === 日期解析 ===
  {{ parse_date_safe("o.plan_date") }}             AS plan_date,
  {{ parse_date_safe("o.actual_date") }}           AS actual_date,
  {{ parse_date_safe("o.delay_expected_date") }}   AS delay_expected_date,
  {{ parse_date_safe("o.original_plan_date") }}    AS original_plan_date,
  {{ parse_date_safe("o.last_update_time") }}      AS last_update_time,

  -- === 周数 ===
  {{ parse_numeric_safe("o.plan_week") }}::int     AS plan_week,
  {{ parse_numeric_safe("o.actual_week") }}::int   AS actual_week,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM {{ parse_date_safe("o.plan_date") }})::int            AS plan_year,
  EXTRACT(QUARTER FROM {{ parse_date_safe("o.plan_date") }})::int         AS plan_quarter,
  to_char({{ parse_date_safe("o.plan_date") }}, 'YYYY-MM')                AS plan_month,
  EXTRACT(WEEK FROM {{ parse_date_safe("o.plan_date") }})::int            AS plan_week_of_year,
  to_char({{ parse_date_safe("o.plan_date") }}, 'IYYY-"W"IW')            AS plan_iso_week,

  EXTRACT(YEAR FROM {{ parse_date_safe("o.actual_date") }})::int          AS actual_year,
  to_char({{ parse_date_safe("o.actual_date") }}, 'YYYY-MM')              AS actual_month,

  -- === 衍生字段 ===
  CASE
    WHEN {{ parse_date_safe("o.plan_date") }} IS NOT NULL
     AND {{ parse_date_safe("o.actual_date") }} IS NOT NULL
    THEN ({{ parse_date_safe("o.actual_date") }} - {{ parse_date_safe("o.plan_date") }})::int
  END AS delay_days,

  CASE
    WHEN {{ parse_date_safe("o.plan_date") }} IS NOT NULL
     AND {{ parse_date_safe("o.plan_date") }} <= current_date
    THEN true
    ELSE false
  END AS is_due,

  'ods_project_subject_domain'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'project_subject_domain') }} o
LEFT JOIN {{ ref('dim_completion_status') }} cs
  ON cs.code = {{ nullif_placeholder("o.completion_status") }}
LEFT JOIN {{ ref('dim_node_type') }} nt
  ON nt.code = {{ nullif_placeholder("o.node_type") }}
WHERE btrim(COALESCE(o.project_no, '')) != ''
  AND btrim(COALESCE(o.plan_date, '')) != ''

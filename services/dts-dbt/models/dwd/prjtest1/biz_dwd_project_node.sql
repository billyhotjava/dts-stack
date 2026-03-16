{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'admin-data-lake']) }}

{{
  config(
    materialized='table',
    tags=['project-management', 'biz', 'dwd']
  )
}}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.subsystem), '') || '|' ||
    COALESCE(btrim(o.node_task), '') || '|' ||
    COALESCE(btrim(o.plan_date), '')
  ) AS node_id,

  -- === 原始业务字段 ===
  NULLIF(btrim(o.project_no), '')          AS project_no,
  NULLIF(btrim(o.subsystem), '')           AS subsystem,
  NULLIF(btrim(o.node_task), '')           AS node_task,
  NULLIF(btrim(o.owner), '')               AS owner,
  NULLIF(btrim(o.dept), '')                AS dept,
  NULLIF(btrim(o.dept_leader), '')         AS dept_leader,
  NULLIF(btrim(o.collab_dept), '')         AS collab_dept,
  NULLIF(btrim(o.supervisor_dept), '')     AS supervisor_dept,
  NULLIF(btrim(o.incomplete_reason), '')   AS incomplete_reason,
  NULLIF(btrim(o.risk_content), '')        AS risk_content,
  NULLIF(btrim(o.delay_impact), '')        AS delay_impact,
  NULLIF(btrim(o.institute_leader), '')    AS institute_leader,
  NULLIF(btrim(o.project_manager), '')     AS project_manager,
  NULLIF(btrim(o.filled_by), '')           AS filled_by,
  NULLIF(btrim(o.highlight), '')           AS highlight,

  -- === 枚举标准化 ===
  NULLIF(btrim(o.completion_status), '')   AS completion_status,
  COALESCE(cs.is_completed, false)         AS is_completed,
  COALESCE(cs.is_on_time, false)           AS is_on_time,
  COALESCE(cs.is_overdue_completed, false) AS is_overdue_completed,
  COALESCE(cs.is_incomplete, false)        AS is_incomplete,

  NULLIF(btrim(o.node_type), '')           AS node_type,
  COALESCE(nt.is_general, false)           AS is_general_node,

  NULLIF(btrim(o.risk_level), '')          AS risk_level,

  NULLIF(btrim(o.source), '')              AS data_source,
  NULLIF(btrim(o.delay_applied), '')       AS delay_applied,

  -- === 日期解析 ===
  {{ parse_date_safe("o.plan_date") }}             AS plan_date,
  {{ parse_date_safe("o.actual_date") }}           AS actual_date,
  {{ parse_date_safe("o.delay_expected_date") }}   AS delay_expected_date,
  {{ parse_date_safe("o.original_plan_date") }}    AS original_plan_date,
  {{ parse_date_safe("o.last_update_time") }}      AS last_update_time,

  -- === 周数 ===
  CASE WHEN o.plan_week ~ '^\d+$' THEN o.plan_week::int END     AS plan_week,
  CASE WHEN o.actual_week ~ '^\d+$' THEN o.actual_week::int END AS actual_week,

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

  '{{ var("project_management_ods_table", "project_subject_domain") }}'::text AS source_table,
  now() AS etl_time

FROM {{ ref('ods_project_subject_domain') }} o
LEFT JOIN {{ ref('dim_completion_status') }} cs
  ON cs.code = NULLIF(btrim(o.completion_status), '')
LEFT JOIN {{ ref('dim_node_type') }} nt
  ON nt.code = NULLIF(btrim(o.node_type), '')
WHERE btrim(COALESCE(o.project_no, '')) != ''
  AND btrim(COALESCE(o.plan_date, '')) != ''

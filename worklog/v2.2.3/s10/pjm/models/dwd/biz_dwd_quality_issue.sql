{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'quality']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.issue_name), '') || '|' ||
    COALESCE(btrim(o.issue_date), '')
  ) AS issue_id,

  -- === 原始业务字段 ===
  {{ nullif_placeholder("o.project_no") }}           AS project_no,
  {{ nullif_placeholder("o.subsystem") }}            AS subsystem,
  {{ nullif_placeholder("o.issue_name") }}           AS issue_name,
  {{ nullif_placeholder("o.dept") }}                 AS dept,
  {{ nullif_placeholder("o.team_leader") }}          AS team_leader,
  {{ nullif_placeholder("o.dept_leader") }}          AS dept_leader,
  {{ nullif_placeholder("o.issue_summary") }}        AS issue_summary,
  {{ nullif_placeholder("o.issue_category") }}       AS issue_category,
  {{ nullif_placeholder("o.zero_plan") }}            AS zero_plan,
  {{ nullif_placeholder("o.zero_plan_synced") }}     AS zero_plan_synced,
  {{ nullif_placeholder("o.status") }}               AS status,
  {{ nullif_placeholder("o.current_progress") }}     AS current_progress,
  {{ nullif_placeholder("o.project_manager") }}      AS project_manager,
  {{ nullif_placeholder("o.filled_by") }}            AS filled_by,

  -- === 数值字段 ===
  {{ parse_numeric_safe("o.new_plan_count") }}::int  AS new_plan_count,

  -- === 日期解析 ===
  {{ parse_date_safe("o.issue_date") }}              AS issue_date,
  {{ parse_date_safe("o.zero_complete_date") }}      AS zero_complete_date,
  {{ parse_date_safe("o.last_update_time") }}        AS last_update_time,

  -- === 周数字段 ===
  {{ parse_numeric_safe("o.issue_week") }}::int              AS issue_week,
  {{ parse_numeric_safe("o.zero_complete_week") }}::int      AS zero_complete_week,
  {{ parse_numeric_safe("o.last_update_week") }}::int        AS last_update_week,

  -- === 闭环标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.status") }}, '')) IN ('已归零', '已闭环') THEN true
    ELSE false
  END AS is_closed,

  -- === 归零计划标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.zero_plan") }}, '')) IN ('无', '') THEN false
    WHEN {{ nullif_placeholder("o.zero_plan") }} IS NULL THEN false
    ELSE true
  END AS has_zero_plan,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM {{ parse_date_safe("o.issue_date") }})::int     AS issue_year,
  EXTRACT(QUARTER FROM {{ parse_date_safe("o.issue_date") }})::int  AS issue_quarter,
  to_char({{ parse_date_safe("o.issue_date") }}, 'YYYY-MM')         AS issue_month,

  -- === 滞留天数 ===
  CASE
    WHEN {{ parse_date_safe("o.issue_date") }} IS NOT NULL
     AND btrim(COALESCE({{ nullif_placeholder("o.status") }}, '')) NOT IN ('已归零', '已闭环')
    THEN (current_date - {{ parse_date_safe("o.issue_date") }})::int
    ELSE 0
  END AS pending_days,

  'ods_quality_issue'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'quality_issue') }} o
WHERE o.project_no IS NOT NULL
  AND btrim(COALESCE(o.project_no, '')) != ''

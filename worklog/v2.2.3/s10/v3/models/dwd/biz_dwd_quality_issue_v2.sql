{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'quality']) }}

WITH cleaned AS (
  SELECT
    {{ nullif_placeholder("o.project_no") }}                AS project_no,
    {{ nullif_placeholder("o.subsystem") }}                 AS subsystem,
    {{ nullif_placeholder("o.issue_name") }}                AS issue_name,
    {{ nullif_placeholder("o.dept") }}                      AS dept,
    {{ nullif_placeholder("o.team_leader") }}               AS team_leader,
    {{ nullif_placeholder("o.dept_leader") }}               AS dept_leader,
    {{ nullif_placeholder("o.issue_summary") }}             AS issue_summary,
    {{ nullif_placeholder("o.issue_category") }}            AS issue_category,
    {{ nullif_placeholder("o.zero_plan") }}                 AS zero_plan,
    {{ nullif_placeholder("o.zero_plan_synced") }}          AS zero_plan_synced,
    {{ nullif_placeholder("o.status") }}                    AS status_raw,
    {{ nullif_placeholder("o.current_progress") }}          AS current_progress,
    {{ nullif_placeholder("o.project_manager") }}           AS project_manager,
    {{ nullif_placeholder("o.filled_by") }}                 AS filled_by,
    {{ nullif_placeholder("o.issue_date") }}                AS issue_date_raw,
    {{ parse_numeric_safe("o.new_plan_count") }}::int       AS new_plan_count,
    {{ parse_date_safe("o.issue_date") }}                   AS issue_date,
    {{ parse_date_safe("o.zero_complete_date") }}           AS zero_complete_date,
    {{ parse_date_safe("o.last_update_time") }}             AS last_update_time,
    {{ parse_numeric_safe("o.issue_week") }}::int           AS issue_week,
    {{ parse_numeric_safe("o.zero_complete_week") }}::int   AS zero_complete_week,
    {{ parse_numeric_safe("o.last_update_week") }}::int     AS last_update_week
  FROM {{ source('pm_ods_v2', 'quality_issue_v2') }} o
),
typed AS (
  SELECT
    c.*,
    CASE
      WHEN btrim(COALESCE(c.zero_plan, '')) IN ('无', '') THEN false
      ELSE true
    END                                                    AS has_zero_plan,
    EXTRACT(YEAR FROM c.issue_date)::int                   AS issue_year,
    EXTRACT(QUARTER FROM c.issue_date)::int                AS issue_quarter,
    to_char(c.issue_date, 'YYYY-MM')                       AS issue_month
  FROM cleaned c
  WHERE c.project_no IS NOT NULL
    AND c.issue_date IS NOT NULL
)

SELECT
  -- === 主键 ===
  md5(
    COALESCE(t.project_no, '') || '|' ||
    COALESCE(t.issue_name, '') || '|' ||
    COALESCE(t.issue_date_raw, '')
  ) AS issue_id,

  -- === 原始业务字段 ===
  t.project_no,
  t.subsystem,
  t.issue_name,
  t.dept,
  t.team_leader,
  t.dept_leader,
  t.issue_summary,
  t.issue_category,
  t.zero_plan,
  t.zero_plan_synced,
  COALESCE(qs.standard_code, t.status_raw)                  AS status,
  t.status_raw,
  t.current_progress,
  t.project_manager,
  t.filled_by,

  -- === 枚举标准化 ===
  COALESCE(qs.is_zero_completed, false)                    AS is_zero_completed,
  COALESCE(qs.is_tech_zero, false)                         AS is_tech_zero,
  COALESCE(qs.is_mgmt_zero, false)                         AS is_mgmt_zero,
  COALESCE(qs.is_both_zero, false)                         AS is_both_zero,

  COALESCE(qc.cat_design, false)                           AS cat_design,
  COALESCE(qc.cat_process, false)                          AS cat_process,
  COALESCE(qc.cat_management, false)                       AS cat_management,
  COALESCE(qc.cat_component, false)                        AS cat_component,
  COALESCE(qc.cat_operation, false)                        AS cat_operation,
  COALESCE(qc.cat_outsource, false)                        AS cat_outsource,
  COALESCE(qc.cat_software, false)                         AS cat_software,
  COALESCE(qc.cat_environment, false)                      AS cat_environment,
  CASE
    WHEN qc.code IS NULL OR COALESCE(qc.cat_other, false)
    THEN true ELSE false
  END                                                      AS cat_other,

  -- === 数值字段 ===
  t.new_plan_count,

  -- === 日期解析 ===
  t.issue_date,
  t.zero_complete_date,
  t.last_update_time,

  -- === 周数字段 ===
  t.issue_week,
  t.zero_complete_week,
  t.last_update_week,

  -- === 归零计划标志 ===
  t.has_zero_plan,

  -- === 时间维度标签 ===
  t.issue_year,
  t.issue_quarter,
  t.issue_month,

  -- === 滞留天数 ===
  CASE
    WHEN COALESCE(qs.is_zero_completed, false) = false
    THEN (current_date - t.issue_date)::int
    ELSE 0
  END AS pending_days,

  'ods_quality_issue_v2'::text AS source_table,
  now() AS etl_time

FROM typed t
LEFT JOIN {{ ref('dim_quality_status_v2') }} qs
  ON qs.code = t.status_raw
LEFT JOIN {{ ref('dim_quality_category_v2') }} qc
  ON qc.code = t.issue_category

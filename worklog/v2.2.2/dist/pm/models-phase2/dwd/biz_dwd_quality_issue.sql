{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'quality']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.issue_name), '') || '|' ||
    COALESCE(btrim(o.issue_date), '')
  ) AS issue_id,

  -- === 原始业务字段 ===
  {{ nullif_placeholder("o.project_no") }}       AS project_no,
  {{ nullif_placeholder("o.issue_name") }}       AS issue_name,
  {{ nullif_placeholder("o.issue_category") }}   AS issue_category,
  {{ nullif_placeholder("o.status") }}           AS status,
  {{ nullif_placeholder("o.closure_status") }}   AS closure_status,
  {{ nullif_placeholder("o.zero_plan") }}        AS zero_plan,
  {{ nullif_placeholder("o.dept") }}             AS dept,
  {{ nullif_placeholder("o.subsystem") }}        AS subsystem,
  {{ nullif_placeholder("o.owner") }}            AS owner,
  {{ nullif_placeholder("o.filled_by") }}        AS filled_by,
  {{ nullif_placeholder("o.remark") }}           AS remark,

  -- === 状态标准化 ===
  COALESCE(qs.is_zero_completed, false) AS is_zero_completed,
  COALESCE(qs.is_tech_zero, false)      AS is_tech_zero,
  COALESCE(qs.is_mgmt_zero, false)      AS is_mgmt_zero,
  COALESCE(qs.is_both_zero, false)      AS is_both_zero,

  -- === 闭环标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.closure_status") }}, '')) = '已闭环' THEN true
    ELSE false
  END AS is_closed,

  -- === 归零计划标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.zero_plan") }}, '')) IN ('无', '') THEN false
    WHEN {{ nullif_placeholder("o.zero_plan") }} IS NULL THEN false
    ELSE true
  END AS has_zero_plan,

  -- === 日期解析 ===
  {{ parse_date_safe("o.issue_date") }}          AS issue_date,
  {{ parse_date_safe("o.last_update_time") }}    AS last_update_time,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM {{ parse_date_safe("o.issue_date") }})::int     AS issue_year,
  EXTRACT(QUARTER FROM {{ parse_date_safe("o.issue_date") }})::int  AS issue_quarter,
  to_char({{ parse_date_safe("o.issue_date") }}, 'YYYY-MM')         AS issue_month,

  -- === 滞留天数 ===
  CASE
    WHEN {{ parse_date_safe("o.issue_date") }} IS NOT NULL
     AND NOT COALESCE(qs.is_zero_completed, false)
    THEN (current_date - {{ parse_date_safe("o.issue_date") }})::int
    ELSE 0
  END AS pending_days,

  'ods_quality_issue'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'quality_issue') }} o
LEFT JOIN {{ ref('dim_quality_status') }} qs
  ON qs.code = {{ nullif_placeholder("o.status") }}
WHERE btrim(COALESCE(o.project_no, '')) != ''

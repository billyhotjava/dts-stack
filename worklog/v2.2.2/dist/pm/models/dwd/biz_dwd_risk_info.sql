{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'risk']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.risk_name), '') || '|' ||
    COALESCE(btrim(o.risk_submit_time), '')
  ) AS risk_id,

  -- === 原始业务字段 ===
  {{ nullif_placeholder("o.project_no") }}          AS project_no,
  {{ nullif_placeholder("o.risk_name") }}           AS risk_name,
  {{ nullif_placeholder("o.risk_level") }}          AS risk_level,
  {{ nullif_placeholder("o.risk_content") }}        AS risk_content,
  {{ nullif_placeholder("o.impact_scope") }}        AS impact_scope,
  {{ nullif_placeholder("o.closure_status") }}      AS closure_status,
  {{ nullif_placeholder("o.response_measure") }}    AS response_measure,
  {{ nullif_placeholder("o.dept") }}                AS dept,
  {{ nullif_placeholder("o.subsystem") }}           AS subsystem,
  {{ nullif_placeholder("o.owner") }}               AS owner,
  {{ nullif_placeholder("o.filled_by") }}           AS filled_by,
  {{ nullif_placeholder("o.remark") }}              AS remark,

  -- === 风险等级标准化 ===
  CASE COALESCE({{ nullif_placeholder("o.risk_level") }}, '')
    WHEN '高' THEN 3
    WHEN '中' THEN 2
    WHEN '低' THEN 1
    ELSE 0
  END AS risk_rank,

  -- === 闭环标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.closure_status") }}, '')) = '已闭环' THEN true
    ELSE false
  END AS is_closed,

  -- === 日期解析 ===
  {{ parse_date_safe("o.risk_submit_time") }}      AS risk_submit_date,
  {{ parse_date_safe("o.last_update_time") }}      AS last_update_time,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM {{ parse_date_safe("o.risk_submit_time") }})::int     AS submit_year,
  EXTRACT(QUARTER FROM {{ parse_date_safe("o.risk_submit_time") }})::int  AS submit_quarter,
  to_char({{ parse_date_safe("o.risk_submit_time") }}, 'YYYY-MM')         AS submit_month,

  -- === 滞留天数 ===
  CASE
    WHEN {{ parse_date_safe("o.risk_submit_time") }} IS NOT NULL
     AND btrim(COALESCE({{ nullif_placeholder("o.closure_status") }}, '')) != '已闭环'
    THEN (current_date - {{ parse_date_safe("o.risk_submit_time") }})::int
    ELSE 0
  END AS pending_days,

  'ods_risk_info'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'risk_info') }} o
WHERE btrim(COALESCE(o.project_no, '')) != ''

{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'cost']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.accounting_period), '')
  ) AS cost_id,

  -- === 原始业务字段 ===
  {{ nullif_placeholder("o.project_no") }}          AS project_no,
  {{ nullif_placeholder("o.project_name") }}        AS project_name,
  {{ nullif_placeholder("o.dept") }}                AS dept,
  {{ nullif_placeholder("o.cost_category") }}       AS cost_category,
  {{ nullif_placeholder("o.remark") }}              AS remark,

  -- === 金额解析 (万元) ===
  {{ parse_numeric_safe("o.budget_amount") }}       AS budget_amount,
  {{ parse_numeric_safe("o.actual_amount") }}       AS actual_amount,

  -- === 偏差计算 ===
  COALESCE({{ parse_numeric_safe("o.actual_amount") }}, 0)
    - COALESCE({{ parse_numeric_safe("o.budget_amount") }}, 0) AS deviation_amount,

  CASE
    WHEN COALESCE({{ parse_numeric_safe("o.budget_amount") }}, 0) = 0 THEN NULL
    ELSE ROUND(
      COALESCE({{ parse_numeric_safe("o.actual_amount") }}, 0)::numeric
      / {{ parse_numeric_safe("o.budget_amount") }}::numeric, 4)
  END AS execution_rate,

  CASE
    WHEN COALESCE({{ parse_numeric_safe("o.budget_amount") }}, 0) = 0 THEN NULL
    ELSE ROUND(
      (COALESCE({{ parse_numeric_safe("o.actual_amount") }}, 0) - COALESCE({{ parse_numeric_safe("o.budget_amount") }}, 0))::numeric
      / {{ parse_numeric_safe("o.budget_amount") }}::numeric, 4)
  END AS deviation_rate,

  -- === 周期解析 ===
  {{ nullif_placeholder("o.accounting_period") }}   AS accounting_period,

  -- 尝试从 accounting_period 提取 year/month
  CASE
    WHEN {{ nullif_placeholder("o.accounting_period") }} ~ '^\d{4}$'
      THEN {{ nullif_placeholder("o.accounting_period") }}::int
    WHEN {{ nullif_placeholder("o.accounting_period") }} ~ '^\d{4}-\d{2}$'
      THEN substr({{ nullif_placeholder("o.accounting_period") }}, 1, 4)::int
    ELSE NULL
  END AS period_year,

  CASE
    WHEN {{ nullif_placeholder("o.accounting_period") }} ~ '^\d{4}-\d{2}$'
      THEN {{ nullif_placeholder("o.accounting_period") }}
    ELSE NULL
  END AS period_month,

  'ods_cost_accounting'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'cost_accounting') }} o
WHERE btrim(COALESCE(o.project_no, '')) != ''

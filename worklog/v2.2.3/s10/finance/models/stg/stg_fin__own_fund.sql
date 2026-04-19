{{ config(materialized='view', tags=['finance', 'stg', 'own-fund']) }}

WITH cleaned AS (
  SELECT
    md5(coalesce(cast(year_period as text), '')) AS source_row_id,
    'ods_finance_own_fund'::text AS source_table,
    cast(year_period as text) AS year_period_raw,
    btrim(cast(year_period as text)) AS year_period,
    {{ parse_numeric_safe("career_fund") }}::numeric(15,2) AS career_fund,
    {{ nullif_placeholder("career_note") }} AS career_note,
    {{ parse_numeric_safe("deprec_fund") }}::numeric(15,2) AS deprec_fund,
    {{ nullif_placeholder("deprec_note") }} AS deprec_note,
    {{ parse_numeric_safe("welfare_fund") }}::numeric(15,2) AS welfare_fund,
    {{ parse_numeric_safe("safety_fund") }}::numeric(15,2) AS safety_fund,
    {{ parse_numeric_safe("total") }}::numeric(15,2) AS total
  FROM {{ source('fin_ods', 'own_fund') }}
)

SELECT
  c.source_row_id,
  c.source_table,
  c.year_period_raw,
  c.year_period,
  c.career_fund,
  c.career_note,
  c.deprec_fund,
  c.deprec_note,
  c.welfare_fund,
  c.safety_fund,
  c.total,
  substring(c.year_period from '^\d{4}')::int AS period_year,
  CASE
    WHEN c.year_period LIKE '%年初' THEN 'opening'
    WHEN c.year_period LIKE '%预计增加' THEN 'increase'
    WHEN c.year_period LIKE '%预计使用' THEN 'usage'
    WHEN c.year_period LIKE '%余额' THEN 'balance'
  END AS period_type,
  CASE
    WHEN c.year_period LIKE '%年初' THEN 1
    WHEN c.year_period LIKE '%预计增加' THEN 2
    WHEN c.year_period LIKE '%预计使用' THEN 3
    WHEN c.year_period LIKE '%余额' THEN 4
  END AS period_sort,
  CASE
    WHEN c.year_period LIKE '%余额' THEN true
    ELSE false
  END AS is_balance_row
FROM cleaned c
WHERE c.year_period IS NOT NULL

{{ config(materialized='view', tags=['finance', 'stg', 'own-fund']) }}

SELECT
  md5(coalesce(cast(year_period as text), '')) AS source_row_id,
  'ods_finance_own_fund'::text AS source_table,

  cast(year_period as text) AS year_period_raw,
  nullif(btrim(cast(year_period as text)), '') AS year_period,

  {{ parse_numeric_safe("career_fund") }}::numeric(15,2) AS career_fund,
  {{ nullif_placeholder("career_note") }} AS career_note,
  {{ parse_numeric_safe("deprec_fund") }}::numeric(15,2) AS deprec_fund,
  {{ nullif_placeholder("deprec_note") }} AS deprec_note,
  {{ parse_numeric_safe("welfare_fund") }}::numeric(15,2) AS welfare_fund,
  {{ parse_numeric_safe("safety_fund") }}::numeric(15,2) AS safety_fund,
  {{ parse_numeric_safe("total") }}::numeric(15,2) AS total
FROM {{ source('fin_ods', 'own_fund') }}
WHERE year_period IS NOT NULL

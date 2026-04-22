{{ config(materialized='view', tags=['finance', 'stg', 'own-fund']) }}

SELECT
  md5(
    coalesce(cast(year_num as text), '')
    || '|' || coalesce(cast(fund_source as text), '')
    || '|' || coalesce(cast(fund_category as text), '')
  ) AS source_row_id,
  'ods_finance_own_fund'::text AS source_table,

  {{ parse_numeric_safe("year_num") }}::int AS year_num,

  cast(fund_source as text) AS fund_source_raw,
  {{ nullif_placeholder("fund_source") }} AS fund_source,

  cast(fund_category as text) AS fund_category_raw,
  {{ nullif_placeholder("fund_category") }} AS fund_category,

  {{ parse_numeric_safe("amount") }}::numeric(15,2) AS amount,
  {{ nullif_placeholder("note") }} AS note
FROM {{ source('fin_ods', 'own_fund') }}
WHERE {{ parse_numeric_safe("year_num") }} IS NOT NULL
  AND {{ nullif_placeholder("fund_source") }} IS NOT NULL
  AND {{ nullif_placeholder("fund_category") }} IS NOT NULL

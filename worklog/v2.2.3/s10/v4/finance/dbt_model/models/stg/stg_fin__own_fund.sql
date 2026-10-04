{{ config(materialized='view', tags=['finance', 'stg', 'own-fund']) }}

SELECT
  md5(
    coalesce(cast(year_num as text), '')
    || '|' || coalesce(cast(fund_source as text), '')
    || '|' || coalesce(cast(fund_category as text), '')
  ) AS source_row_id,
  'ods_finance_own_fund'::text AS source_table,

  cast(year_num as integer) AS year_num,

  cast(fund_source as text) AS fund_source_raw,
  nullif(btrim(cast(fund_source as text)), '') AS fund_source,

  cast(fund_category as text) AS fund_category_raw,
  nullif(btrim(cast(fund_category as text)), '') AS fund_category,

  cast(amount as numeric(15,2)) AS amount,
  nullif(btrim(cast(note as text)), '') AS note
FROM {{ source('fin_ods', 'own_fund') }}
WHERE year_num IS NOT NULL
  AND nullif(btrim(cast(fund_source as text)), '') IS NOT NULL
  AND nullif(btrim(cast(fund_category as text)), '') IS NOT NULL

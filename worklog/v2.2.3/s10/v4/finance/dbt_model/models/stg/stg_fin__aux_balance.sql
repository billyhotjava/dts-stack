{{ config(materialized='view', tags=['finance', 'stg', 'aux-balance']) }}

SELECT
  md5(
    coalesce(cast(subject_code as text), '')
    || '|'
    || coalesce(cast(subject_name as text), '')
    || '|'
    || coalesce(cast(dept_name as text), '')
    || '|'
    || coalesce(cast(contract_name as text), '')
    || '|'
    || coalesce(cast(balance as text), '')
  ) AS source_row_id,
  'ods_finance_aux_balance'::text AS source_table,

  nullif(btrim(cast(subject_code as text)), '') AS subject_code,
  nullif(btrim(cast(subject_name as text)), '') AS subject_name,
  nullif(btrim(cast(dept_name as text)), '') AS dept_name,

  cast(contract_name as text) AS contract_name_raw,
  nullif(nullif(btrim(cast(contract_name as text)), ''), '—') AS contract_name,

  cast(balance as numeric(15,2)) AS balance
FROM {{ source('fin_ods', 'aux_balance') }}
WHERE nullif(btrim(cast(subject_code as text)), '') IS NOT NULL

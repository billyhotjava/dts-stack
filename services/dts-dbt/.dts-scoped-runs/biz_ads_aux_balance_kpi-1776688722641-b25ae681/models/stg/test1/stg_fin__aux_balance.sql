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

  {{ nullif_placeholder("subject_code") }} AS subject_code,
  {{ nullif_placeholder("subject_name") }} AS subject_name,
  {{ nullif_placeholder("dept_name") }} AS dept_name,

  cast(contract_name as text) AS contract_name_raw,
  nullif(btrim(cast(contract_name as text)), '') AS contract_name,

  {{ parse_numeric_safe("balance") }}::numeric(15,2) AS balance
FROM {{ source('fin_ods', 'aux_balance') }}
WHERE subject_code IS NOT NULL

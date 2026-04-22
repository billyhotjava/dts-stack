{{ config(materialized='view', tags=['finance', 'stg', 'aux-balance-personal']) }}

SELECT
  md5(
    coalesce(cast(subject_code as text), '')
    || '|'
    || coalesce(cast(subject_name as text), '')
    || '|'
    || coalesce(cast(employee_dept as text), '')
    || '|'
    || coalesce(cast(employee_name as text), '')
    || '|'
    || coalesce(cast(balance as text), '')
  ) AS source_row_id,
  'ods_finance_aux_balance_personal'::text AS source_table,

  {{ nullif_placeholder("subject_code") }} AS subject_code,
  {{ nullif_placeholder("subject_name") }} AS subject_name,
  {{ nullif_placeholder("employee_dept") }} AS employee_dept,
  {{ nullif_placeholder("employee_name") }} AS employee_name,

  {{ parse_numeric_safe("balance") }}::numeric(15,2) AS balance
FROM {{ source('fin_ods', 'aux_balance_personal') }}
WHERE {{ nullif_placeholder("subject_code") }} IS NOT NULL

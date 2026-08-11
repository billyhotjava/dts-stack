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

  nullif(btrim(cast(subject_code as text)), '') AS subject_code,
  nullif(btrim(cast(subject_name as text)), '') AS subject_name,
  nullif(btrim(cast(employee_dept as text)), '') AS employee_dept,
  nullif(btrim(cast(employee_name as text)), '') AS employee_name,

  cast(balance as numeric(15,2)) AS balance
FROM {{ source('fin_ods', 'aux_balance_personal') }}
WHERE nullif(btrim(cast(subject_code as text)), '') IS NOT NULL

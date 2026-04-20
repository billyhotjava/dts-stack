{{ config(materialized='view', tags=['finance', 'stg', 'aux-balance-personal']) }}

WITH cleaned AS (
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
)

SELECT
  c.source_row_id,
  c.source_table,
  c.subject_code,
  c.subject_name,
  c.employee_dept,
  c.employee_name,
  c.balance,
  abs(c.balance) AS abs_balance,
  CASE
    WHEN c.balance > 0 THEN 'debit'
    WHEN c.balance < 0 THEN 'credit'
    ELSE 'zero'
  END AS balance_direction,
  CASE
    WHEN c.subject_code LIKE '1122%' THEN '其他应收-借款'
    WHEN c.subject_code LIKE '2211%' THEN '应付职工薪酬'
    ELSE '其他'
  END AS subject_category
FROM cleaned c
WHERE c.subject_code IS NOT NULL

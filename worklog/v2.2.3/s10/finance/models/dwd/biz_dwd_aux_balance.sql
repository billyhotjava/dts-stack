{{ config(materialized='table', tags=['finance', 'biz', 'dwd', 'aux-balance']) }}

SELECT
  t.source_row_id AS aux_balance_id,
  t.source_row_id,
  t.source_table,
  t.subject_code,
  t.subject_name,
  t.dept_name,
  t.contract_name_raw,
  t.contract_name,
  t.contract_name_norm,
  t.balance,
  t.abs_balance,
  t.balance_sign,
  t.expense_category,
  d.expense_category_id,
  t.has_contract,
  now() AS etl_time
FROM {{ ref('stg_fin__aux_balance') }} t
LEFT JOIN {{ ref('dim_expense_category') }} d
  ON d.code = t.expense_category

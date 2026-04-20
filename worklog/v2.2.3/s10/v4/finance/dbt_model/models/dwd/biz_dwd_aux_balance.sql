{{ config(materialized='table', tags=['finance', 'biz', 'dwd', 'aux-balance']) }}

WITH stg AS (
  SELECT * FROM {{ ref('stg_fin__aux_balance') }}
),
normalized AS (
  SELECT
    s.*,
    substring(s.subject_code from 1 for 4) AS subject_code_prefix,
    coalesce(p.expense_category_code, '其他') AS expense_category
  FROM stg s
  LEFT JOIN {{ ref('dim_expense_code_prefix') }} p
    ON p.prefix = substring(s.subject_code from 1 for 4)
),
derived AS (
  SELECT
    n.*,
    abs(n.balance) AS abs_balance,
    CASE
      WHEN n.balance > 0 THEN 'positive'
      WHEN n.balance < 0 THEN 'negative'
      ELSE 'zero'
    END AS balance_sign,
    CASE
      WHEN n.contract_name IS NOT NULL AND n.contract_name <> '—' THEN n.contract_name
    END AS contract_name_norm,
    CASE
      WHEN n.contract_name IS NOT NULL AND n.contract_name <> '—' THEN true
      ELSE false
    END AS has_contract
  FROM normalized n
)

SELECT
  d.source_row_id AS aux_balance_id,
  d.source_row_id,
  d.source_table,

  d.subject_code,
  d.subject_code_prefix,
  d.subject_name,
  d.dept_name,

  d.contract_name_raw,
  d.contract_name,
  d.contract_name_norm,
  d.has_contract,

  d.balance,
  d.abs_balance,
  d.balance_sign,

  d.expense_category,
  dec.expense_category_id,
  dec.label AS expense_category_label,
  dec.sort_order AS expense_category_sort,

  now() AS etl_time
FROM derived d
LEFT JOIN {{ ref('dim_expense_category') }} dec
  ON dec.code = d.expense_category

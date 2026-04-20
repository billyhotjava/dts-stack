{{ config(materialized='table', tags=['finance', 'biz', 'dwd', 'aux-balance-personal']) }}

WITH stg AS (
  SELECT * FROM {{ ref('stg_fin__aux_balance_personal') }}
),
normalized AS (
  SELECT
    s.*,
    substring(s.subject_code from 1 for 4) AS subject_code_prefix,
    coalesce(p.subject_category_code, '其他') AS subject_category
  FROM stg s
  LEFT JOIN {{ ref('dim_personal_subject_code_prefix') }} p
    ON p.prefix = substring(s.subject_code from 1 for 4)
),
derived AS (
  SELECT
    n.*,
    abs(n.balance) AS abs_balance,
    CASE
      WHEN n.balance > 0 THEN 'debit'
      WHEN n.balance < 0 THEN 'credit'
      ELSE 'zero'
    END AS balance_direction
  FROM normalized n
)

SELECT
  d.source_row_id AS personal_balance_id,
  d.source_row_id,
  d.source_table,

  d.subject_code,
  d.subject_code_prefix,
  d.subject_name,
  d.employee_dept,
  d.employee_name,

  d.balance,
  d.abs_balance,
  d.balance_direction,
  bd.balance_direction_id,
  bd.label AS balance_direction_label,

  d.subject_category,
  sc.subject_category_id,
  sc.label AS subject_category_label,
  sc.sort_order AS subject_category_sort,

  now() AS etl_time
FROM derived d
LEFT JOIN {{ ref('dim_balance_direction') }} bd
  ON bd.code = d.balance_direction
LEFT JOIN {{ ref('dim_personal_subject_category') }} sc
  ON sc.code = d.subject_category



WITH base AS (
  SELECT
    coalesce(dept_name, '未分配部门') AS dept_name,
    subject_code,
    contract_name_norm,
    expense_category,
    balance,
    abs_balance
  FROM "biadmin"."public"."biz_dwd_aux_balance"
)

SELECT
  dept_name,
  coalesce(sum(balance), 0)::numeric(15,2) AS dept_balance,
  coalesce(sum(abs_balance), 0)::numeric(15,2) AS dept_abs_balance,
  count(DISTINCT subject_code) AS subject_count,
  count(DISTINCT contract_name_norm) AS contract_count,
  count(DISTINCT expense_category) AS expense_category_count,
  count(*) AS record_count,
  now() AS etl_time
FROM base
GROUP BY dept_name
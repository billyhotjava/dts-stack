

WITH base AS (
  SELECT
    coalesce(employee_dept, '未分配部门') AS employee_dept,
    employee_name,
    subject_code,
    balance,
    abs_balance,
    balance_direction
  FROM "biadmin"."public"."biz_dwd_aux_balance_personal"
)

SELECT
  employee_dept,
  coalesce(sum(balance), 0)::numeric(15,2) AS net_balance,
  coalesce(sum(CASE WHEN balance_direction = 'debit' THEN balance ELSE 0 END), 0)::numeric(15,2) AS debit_total,
  coalesce(sum(CASE WHEN balance_direction = 'credit' THEN abs_balance ELSE 0 END), 0)::numeric(15,2) AS credit_total,
  coalesce(sum(abs_balance), 0)::numeric(15,2) AS abs_total,
  count(DISTINCT employee_name) AS employee_count,
  count(DISTINCT subject_code) AS subject_count,
  count(*) AS record_count,
  now() AS etl_time
FROM base
GROUP BY employee_dept
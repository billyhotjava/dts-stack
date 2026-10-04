{{ config(materialized='table', tags=['finance', 'biz', 'ads', 'kpi', 'aux-balance']) }}

WITH base AS (
  SELECT
    coalesce(dept_name, '未分配部门') AS dept_name,
    subject_code,
    contract_name_norm,
    balance,
    abs_balance
  FROM {{ ref('biz_dwd_aux_balance') }}
)

SELECT
  coalesce(sum(balance), 0)::numeric(15,2) AS total_balance,
  coalesce(sum(abs_balance), 0)::numeric(15,2) AS total_abs_balance,
  count(DISTINCT subject_code) AS subject_count,
  count(DISTINCT contract_name_norm) AS contract_count,
  count(DISTINCT dept_name) AS dept_count,
  count(*) AS record_count,
  now() AS etl_time
FROM base

{{ config(materialized='table', tags=['finance', 'biz', 'ads', 'kpi', 'project-fund']) }}

WITH base AS (
  SELECT *
  FROM {{ ref('biz_dws_project_fund_summary') }}
)

SELECT
  summary_scope,
  project_count,
  record_count,
  sum_total_fund,
  sum_direct_ctrl,
  sum_reserve_indirect,
  sum_direct_spent,
  sum_indirect_spent,
  sum_total_spent,
  sum_remaining_fund,
  CASE
    WHEN sum_direct_ctrl > 0 THEN round(sum_direct_spent * 100.0 / sum_direct_ctrl, 2)
    ELSE 0
  END AS overall_direct_rate,
  CASE
    WHEN sum_total_fund > 0 THEN round(sum_total_spent * 100.0 / sum_total_fund, 2)
    ELSE 0
  END AS overall_total_rate,
  CASE
    WHEN sum_reserve_indirect > 0 THEN round(sum_indirect_spent * 100.0 / sum_reserve_indirect, 2)
    ELSE 0
  END AS overall_indirect_rate,
  direct_rate_over_100_count,
  total_rate_over_100_count,
  avg_cycle_month_span,
  earliest_cycle_start_date,
  latest_cycle_end_date,
  now() AS etl_time
FROM base

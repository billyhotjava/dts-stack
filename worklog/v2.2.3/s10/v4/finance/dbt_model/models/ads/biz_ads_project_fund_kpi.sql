{{ config(materialized='table', tags=['finance', 'biz', 'ads', 'kpi', 'project-fund']) }}

-- 项目经费 KPI
-- 多 scope：all_projects / by_major / by_status
-- 新增：已收款/待收款比率与金额口径

WITH base AS (
  SELECT * FROM {{ ref('biz_dws_project_fund_summary') }}
)

SELECT
  summary_scope,
  scope_key,
  scope_label,

  project_count,
  record_count,
  major_project_count,
  completed_project_count,

  sum_total_fund,
  sum_direct_ctrl,
  sum_reserve_indirect,
  sum_direct_spent,
  sum_indirect_spent,
  sum_total_spent,
  sum_remaining_fund,

  sum_received_fund,
  sum_receivable_fund,
  sum_outstanding_fund,

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

  CASE
    WHEN sum_total_fund > 0 THEN round(sum_received_fund * 100.0 / sum_total_fund, 2)
    ELSE 0
  END AS overall_received_rate,

  CASE
    WHEN sum_total_fund > 0 THEN round(sum_outstanding_fund * 100.0 / sum_total_fund, 2)
    ELSE 0
  END AS overall_outstanding_rate,

  direct_rate_over_100_count,
  total_rate_over_100_count,

  avg_cycle_month_span,
  earliest_cycle_start_date,
  latest_cycle_end_date,

  now() AS etl_time
FROM base
ORDER BY summary_scope, scope_key

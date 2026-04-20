{{ config(materialized='table', tags=['finance', 'biz', 'ads', 'kpi', 'own-fund']) }}

-- 年度基金 KPI
-- 直接消费 DWS：每行 = (year_num, fund_category)，含 fund_category='全部' 的年度合计
-- 年末余额 = derived_balance = opening + increase - usage

SELECT
  year_num,
  fund_category_code,
  fund_category_label,
  fund_category_sort,

  opening_amount,
  increase_amount,
  usage_amount,
  derived_balance AS year_end_balance,

  usage_rate,
  growth_rate,

  CASE
    WHEN usage_rate > 95 THEN 'danger'
    WHEN usage_rate > 80 THEN 'warning'
    ELSE 'healthy'
  END AS usage_rate_level,

  CASE
    WHEN growth_rate > 0 THEN 'growth'
    WHEN growth_rate < 0 THEN 'decline'
    ELSE 'flat'
  END AS growth_direction,

  source_count,
  record_count,
  is_complete_sources,

  now() AS etl_time
FROM {{ ref('biz_dws_own_fund_yearly') }}
ORDER BY year_num, fund_category_sort

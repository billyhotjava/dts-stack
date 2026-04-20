

SELECT
  period_year,
  opening_total,
  increase_total,
  usage_total,
  balance_total,
  career_balance,
  deprec_balance,
  welfare_balance,
  safety_balance,
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
  record_count,
  period_type_count,
  is_complete_year,
  is_total_balanced,
  reconciliation_gap,
  now() AS etl_time
FROM "biadmin"."public"."biz_dws_own_fund_yearly"
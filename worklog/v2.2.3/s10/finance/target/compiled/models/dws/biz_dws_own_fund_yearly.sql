

WITH base AS (
  SELECT *
  FROM "biadmin"."public"."biz_dwd_own_fund"
),
yearly AS (
  SELECT
    period_year,
    count(*) AS record_count,
    count(DISTINCT period_type) AS period_type_count,
    coalesce(sum(CASE WHEN period_type = 'opening' THEN career_fund ELSE 0 END), 0)::numeric(15,2) AS opening_career,
    coalesce(sum(CASE WHEN period_type = 'opening' THEN deprec_fund ELSE 0 END), 0)::numeric(15,2) AS opening_deprec,
    coalesce(sum(CASE WHEN period_type = 'opening' THEN welfare_fund ELSE 0 END), 0)::numeric(15,2) AS opening_welfare,
    coalesce(sum(CASE WHEN period_type = 'opening' THEN safety_fund ELSE 0 END), 0)::numeric(15,2) AS opening_safety,
    coalesce(sum(CASE WHEN period_type = 'increase' THEN career_fund ELSE 0 END), 0)::numeric(15,2) AS increase_career,
    coalesce(sum(CASE WHEN period_type = 'increase' THEN deprec_fund ELSE 0 END), 0)::numeric(15,2) AS increase_deprec,
    coalesce(sum(CASE WHEN period_type = 'increase' THEN welfare_fund ELSE 0 END), 0)::numeric(15,2) AS increase_welfare,
    coalesce(sum(CASE WHEN period_type = 'increase' THEN safety_fund ELSE 0 END), 0)::numeric(15,2) AS increase_safety,
    coalesce(sum(CASE WHEN period_type = 'usage' THEN career_fund ELSE 0 END), 0)::numeric(15,2) AS usage_career,
    coalesce(sum(CASE WHEN period_type = 'usage' THEN deprec_fund ELSE 0 END), 0)::numeric(15,2) AS usage_deprec,
    coalesce(sum(CASE WHEN period_type = 'usage' THEN welfare_fund ELSE 0 END), 0)::numeric(15,2) AS usage_welfare,
    coalesce(sum(CASE WHEN period_type = 'usage' THEN safety_fund ELSE 0 END), 0)::numeric(15,2) AS usage_safety,
    coalesce(sum(CASE WHEN period_type = 'balance' THEN career_fund ELSE 0 END), 0)::numeric(15,2) AS career_balance,
    coalesce(sum(CASE WHEN period_type = 'balance' THEN deprec_fund ELSE 0 END), 0)::numeric(15,2) AS deprec_balance,
    coalesce(sum(CASE WHEN period_type = 'balance' THEN welfare_fund ELSE 0 END), 0)::numeric(15,2) AS welfare_balance,
    coalesce(sum(CASE WHEN period_type = 'balance' THEN safety_fund ELSE 0 END), 0)::numeric(15,2) AS safety_balance,
    coalesce(sum(CASE WHEN period_type = 'opening' THEN total ELSE 0 END), 0)::numeric(15,2) AS opening_total,
    coalesce(sum(CASE WHEN period_type = 'increase' THEN total ELSE 0 END), 0)::numeric(15,2) AS increase_total,
    coalesce(sum(CASE WHEN period_type = 'usage' THEN total ELSE 0 END), 0)::numeric(15,2) AS usage_total,
    coalesce(sum(CASE WHEN period_type = 'balance' THEN total ELSE 0 END), 0)::numeric(15,2) AS balance_total,
    bool_and(is_total_balanced) AS is_total_balanced
  FROM base
  GROUP BY period_year
)

SELECT
  period_year,
  record_count,
  period_type_count,
  opening_career,
  opening_deprec,
  opening_welfare,
  opening_safety,
  increase_career,
  increase_deprec,
  increase_welfare,
  increase_safety,
  usage_career,
  usage_deprec,
  usage_welfare,
  usage_safety,
  career_balance,
  deprec_balance,
  welfare_balance,
  safety_balance,
  opening_total,
  increase_total,
  usage_total,
  balance_total,
  (opening_total + increase_total - usage_total - balance_total)::numeric(15,2) AS reconciliation_gap,
  is_total_balanced,
  CASE
    WHEN period_type_count = 4 THEN true
    ELSE false
  END AS is_complete_year,
  CASE
    WHEN opening_total + increase_total > 0 THEN
      round(usage_total * 100.0 / (opening_total + increase_total), 2)
    ELSE 0
  END AS usage_rate,
  CASE
    WHEN opening_total > 0 THEN
      round((balance_total - opening_total) * 100.0 / opening_total, 2)
    ELSE 0
  END AS growth_rate,
  now() AS etl_time
FROM yearly
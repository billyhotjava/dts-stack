-- biz_ads_own_fund_kpi.sql
-- ADS层：自有资金最终KPI，包含年度透视数据及余额构成，供大屏直接消费
-- 依赖: biz_dws_own_fund_yearly

SELECT
    period_year,
    opening_total,
    increase_total,
    usage_total,
    balance_total,
    balance_career_fund,
    balance_deprec_fund,
    balance_welfare_fund,
    balance_safety_fund,
    usage_rate,
    growth_rate
FROM biz_dws_own_fund_yearly
ORDER BY period_year

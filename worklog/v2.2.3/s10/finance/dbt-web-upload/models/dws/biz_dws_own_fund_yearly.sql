-- biz_dws_own_fund_yearly.sql
-- DWS层：自有资金按年度透视，每年一行包含年初/增加/使用/余额及KPI指标
-- 依赖: biz_dwd_own_fund

SELECT
    period_year,

    MAX(CASE WHEN period_type = 'opening'  THEN total END) AS opening_total,
    MAX(CASE WHEN period_type = 'increase' THEN total END) AS increase_total,
    MAX(CASE WHEN period_type = 'usage'    THEN total END) AS usage_total,
    MAX(CASE WHEN period_type = 'balance'  THEN total END) AS balance_total,

    -- 各基金余额
    MAX(CASE WHEN period_type = 'balance' THEN career_fund  END) AS balance_career_fund,
    MAX(CASE WHEN period_type = 'balance' THEN deprec_fund  END) AS balance_deprec_fund,
    MAX(CASE WHEN period_type = 'balance' THEN welfare_fund END) AS balance_welfare_fund,
    MAX(CASE WHEN period_type = 'balance' THEN safety_fund  END) AS balance_safety_fund,

    -- 资金使用率(%) = 预计使用 / (年初 + 预计增加) × 100
    ROUND(
        MAX(CASE WHEN period_type = 'usage' THEN total END) * 100.0
        / NULLIF(
            MAX(CASE WHEN period_type = 'opening'  THEN total END)
          + MAX(CASE WHEN period_type = 'increase' THEN total END), 0),
    1) AS usage_rate,

    -- 余额增长率(%) = (年末余额 - 年初) / 年初 × 100
    ROUND(
        (MAX(CASE WHEN period_type = 'balance' THEN total END)
       - MAX(CASE WHEN period_type = 'opening' THEN total END)) * 100.0
        / NULLIF(MAX(CASE WHEN period_type = 'opening' THEN total END), 0),
    1) AS growth_rate

FROM biz_dwd_own_fund
GROUP BY period_year
ORDER BY period_year

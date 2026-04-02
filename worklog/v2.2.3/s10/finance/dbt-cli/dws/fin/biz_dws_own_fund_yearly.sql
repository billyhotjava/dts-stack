{{ config(materialized='table', tags=['finance', 'biz', 'dws']) }}

-- ============================================================
-- 自有资金 DWS 层：按年度透视，每年一行包含四个期间类型的合计
-- 输入：biz_dwd_own_fund（DWD 层解析后的自有资金）
-- 输出：每年度的年初/增加/使用/余额 + 各基金明细
-- ============================================================

SELECT
    period_year,

    -- === 合计列按期间类型透视 ===
    MAX(CASE WHEN period_type = 'opening'  THEN total END) AS opening_total,
    MAX(CASE WHEN period_type = 'increase' THEN total END) AS increase_total,
    MAX(CASE WHEN period_type = 'usage'    THEN total END) AS usage_total,
    MAX(CASE WHEN period_type = 'balance'  THEN total END) AS balance_total,

    -- === 事业基金按期间类型透视 ===
    MAX(CASE WHEN period_type = 'opening'  THEN career_fund END) AS career_opening,
    MAX(CASE WHEN period_type = 'increase' THEN career_fund END) AS career_increase,
    MAX(CASE WHEN period_type = 'usage'    THEN career_fund END) AS career_usage,
    MAX(CASE WHEN period_type = 'balance'  THEN career_fund END) AS career_balance,

    -- === 折旧基金按期间类型透视 ===
    MAX(CASE WHEN period_type = 'opening'  THEN deprec_fund END) AS deprec_opening,
    MAX(CASE WHEN period_type = 'increase' THEN deprec_fund END) AS deprec_increase,
    MAX(CASE WHEN period_type = 'usage'    THEN deprec_fund END) AS deprec_usage,
    MAX(CASE WHEN period_type = 'balance'  THEN deprec_fund END) AS deprec_balance,

    -- === 职工福利基金按期间类型透视 ===
    MAX(CASE WHEN period_type = 'opening'  THEN welfare_fund END) AS welfare_opening,
    MAX(CASE WHEN period_type = 'increase' THEN welfare_fund END) AS welfare_increase,
    MAX(CASE WHEN period_type = 'usage'    THEN welfare_fund END) AS welfare_usage,
    MAX(CASE WHEN period_type = 'balance'  THEN welfare_fund END) AS welfare_balance,

    -- === 安全生产基金按期间类型透视 ===
    MAX(CASE WHEN period_type = 'opening'  THEN safety_fund END) AS safety_opening,
    MAX(CASE WHEN period_type = 'increase' THEN safety_fund END) AS safety_increase,
    MAX(CASE WHEN period_type = 'usage'    THEN safety_fund END) AS safety_usage,
    MAX(CASE WHEN period_type = 'balance'  THEN safety_fund END) AS safety_balance

FROM {{ ref('biz_dwd_own_fund') }}
WHERE period_type IS NOT NULL
GROUP BY period_year
ORDER BY period_year

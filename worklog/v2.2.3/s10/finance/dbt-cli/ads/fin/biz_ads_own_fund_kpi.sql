{{ config(materialized='table', tags=['finance', 'biz', 'ads', 'kpi']) }}

-- ============================================================
-- 自有资金 ADS 层：大屏 KPI 指标
-- 输入：biz_dws_own_fund_yearly（DWS 层年度透视表）
-- 输出：每年度 6 个 KPI 指标，供大屏指标卡片和仪表盘使用
--
-- KPI 说明：
--   opening_total  — 年初合计（年度起始资金总规模）
--   increase_total — 预计增加（本年度新增资金）
--   usage_total    — 预计使用（本年度消耗资金）
--   balance_total  — 年末余额（年度结束资金规模）
--   usage_rate     — 资金使用率 = 预计使用 / (年初 + 预计增加) × 100%
--   growth_rate    — 余额增长率 = (年末余额 - 年初) / 年初 × 100%
-- ============================================================

SELECT
    period_year,

    -- 四个基础 KPI
    opening_total,
    increase_total,
    usage_total,
    balance_total,

    -- 资金使用率（%）：衡量资金消耗强度，>80% 需警惕资金紧张
    ROUND(
        usage_total * 100.0
        / NULLIF(opening_total + increase_total, 0),
    1) AS usage_rate,

    -- 余额增长率（%）：正值=资金规模扩大，负值=资金净消耗
    ROUND(
        (balance_total - opening_total) * 100.0
        / NULLIF(opening_total, 0),
    1) AS growth_rate,

    -- === 环形图数据：年末余额各基金构成 ===
    career_balance,
    deprec_balance,
    welfare_balance,
    safety_balance

FROM {{ ref('biz_dws_own_fund_yearly') }}
ORDER BY period_year

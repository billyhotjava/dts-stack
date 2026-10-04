-- ================================================================
-- 查询卡片: 成本预算KPI
-- 用途: 按月统计成本预算执行情况，包括预算/实际/偏差及执行率
-- 对应大屏: Screen 1（总览）、Screen 5（成本看板）
-- 数据表: biz_ads_cost_kpi
-- ================================================================

SELECT
    period_year,
    period_month,
    budget_total,
    actual_total,
    deviation_total,
    execution_rate,
    deviation_rate,
    annual_budget_total,
    annual_actual_total,
    annual_deviation_total,
    annual_execution_rate,
    annual_deviation_rate
FROM biz_ads_cost_kpi
ORDER BY period_year, period_month

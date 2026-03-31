-- ================================================================
-- 查询卡片: 成本周期汇总
-- 用途: 按项目/部门汇总成本预算执行情况，用于部门预算图表和项目明细表
-- 对应大屏: Screen 5（成本看板）
-- 数据表: biz_dws_cost_period_summary
-- ================================================================

SELECT
    period_year,
    period_month,
    project_no,
    dept,
    budget_total,
    actual_total,
    deviation_total,
    execution_rate,
    deviation_rate,
    project_cnt
FROM biz_dws_cost_period_summary
ORDER BY period_year, period_month, project_no

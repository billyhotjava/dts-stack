-- ================================================================
-- 查询卡片: 风险KPI
-- 用途: 按月统计风险指标，包括风险数量、闭环率、措施覆盖率等
-- 对应大屏: Screen 1（总览）、Screen 6（风险看板）
-- 数据表: biz_ads_risk_kpi
-- ================================================================

SELECT
    period_year,
    period_month,
    total_risk_cnt,
    high_risk_cnt,
    mid_risk_cnt,
    low_risk_cnt,
    closed_cnt,
    open_cnt,
    closure_rate,
    high_risk_project_cnt,
    has_measure_cnt,
    measure_coverage_rate,
    avg_pending_days
FROM biz_ads_risk_kpi
ORDER BY period_year, period_month

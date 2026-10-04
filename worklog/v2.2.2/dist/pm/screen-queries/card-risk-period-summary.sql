-- ================================================================
-- 查询卡片: 风险周期汇总
-- 用途: 按项目/部门汇总风险分布情况，用于风险矩阵图表
-- 对应大屏: Screen 6（风险看板）
-- 数据表: biz_dws_risk_period_summary
-- ================================================================

SELECT
    period_year,
    period_quarter,
    period_month,
    project_no,
    dept,
    total_risk_cnt,
    high_risk_cnt,
    mid_risk_cnt,
    low_risk_cnt,
    closed_cnt,
    open_cnt,
    closure_rate,
    has_measure_cnt,
    measure_coverage_rate,
    avg_pending_days
FROM biz_dws_risk_period_summary
ORDER BY period_year, period_month, project_no

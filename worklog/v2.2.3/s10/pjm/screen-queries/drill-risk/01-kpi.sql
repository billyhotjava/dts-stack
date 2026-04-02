-- ================================================================
-- 查询卡片: 风险域KPI总览
-- 用途: 按月统计风险KPI，包括风险数、释放率、高风险占比等
-- 对应大屏: Screen 7（风险总览）、Screen 9（风险看板KPI）
-- 数据表: biz_ads_risk_kpi
-- ================================================================

SELECT
    period_year,                            -- 统计年度
    period_month,                           -- 统计月份
    total_risk_cnt,                         -- 风险总数
    high_cnt,                               -- 高风险数
    mid_cnt,                                -- 中风险数
    low_cnt,                                -- 低风险数
    released_cnt,                           -- 已释放数
    open_cnt,                               -- 未释放数
    release_rate                            -- 释放率
FROM biz_ads_risk_kpi
ORDER BY period_year, period_month;

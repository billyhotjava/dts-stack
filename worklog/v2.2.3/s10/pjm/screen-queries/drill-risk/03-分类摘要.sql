-- ================================================================
-- 查询卡片: 风险域周期汇总
-- 用途: 按月汇总风险趋势，含新增数、释放数、累计数
-- 对应大屏: Screen 9（风险趋势图）
-- 数据表: biz_dws_risk_period_summary
-- ================================================================

SELECT *
FROM biz_dws_risk_period_summary
ORDER BY period_year, period_month;

-- ================================================================
-- 查询卡片: 技术状态域周期汇总
-- 用途: 按月汇总技术状态变更趋势，含新增数、完成数、累计数
-- 对应大屏: Screen 6（技术状态趋势图）
-- 数据表: biz_dws_tech_state_period_summary
-- ================================================================

SELECT *
FROM biz_dws_tech_state_period_summary
ORDER BY period_year, period_month;

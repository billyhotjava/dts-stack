-- ================================================================
-- 查询卡片: 质量域周期汇总
-- 用途: 按月汇总质量问题趋势，含新增数、闭环数、累计数
-- 对应大屏: Screen 3（质量趋势图）
-- 数据表: biz_dws_quality_period_summary
-- ================================================================

SELECT *
FROM biz_dws_quality_period_summary
ORDER BY period_year, period_month;

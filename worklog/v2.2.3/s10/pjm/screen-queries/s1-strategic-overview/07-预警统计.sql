-- ================================================================
-- 查询卡片: 未完成高风险节点
-- 用途: 按月统计未完成节点中的高风险、中风险、里程碑、重大、重要节点数量
-- 对应大屏: Screen 1、Screen 2
-- 数据表: biz_ads_project_incomplete_risk
-- ================================================================

SELECT
    plan_year,
    plan_quarter,
    plan_month,
    incomplete_high_risk_cnt,
    incomplete_mid_risk_cnt,
    incomplete_milestone_cnt,
    incomplete_major_cnt,
    incomplete_important_cnt
FROM biz_ads_project_incomplete_risk
ORDER BY plan_year, plan_month

-- ================================================================
-- 查询卡片: 未完成高风险节点
-- 用途: 按月统计未完成节点中的高风险、中风险、里程碑、重大、重要节点数量
-- 对应大屏: Screen 1、Screen 2
-- 数据表: biz_ads_project_incomplete_risk
-- ================================================================

SELECT
    plan_year,                              -- 计划年度
    plan_quarter,                           -- 计划季度
    plan_month,                             -- 计划月份
    incomplete_high_risk_cnt,               -- 未完成高风险节点数
    incomplete_mid_risk_cnt,                -- 未完成中风险节点数
    incomplete_milestone_cnt,               -- 未完成里程碑节点数
    incomplete_major_cnt,                   -- 未完成重大节点数
    incomplete_important_cnt                -- 未完成重要节点数
FROM biz_ads_project_incomplete_risk
ORDER BY plan_year, plan_month

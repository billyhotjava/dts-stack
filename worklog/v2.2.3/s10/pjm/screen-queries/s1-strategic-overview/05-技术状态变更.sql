-- ================================================================
-- 查询卡片: 技术状态域KPI总览
-- 用途: 按月统计技术状态KPI，包括变更数、签署完成率等
-- 对应大屏: Screen 4（技术状态总览）、Screen 6（技术状态看板KPI）
-- 数据表: biz_ads_tech_state_kpi
-- ================================================================

SELECT *
FROM biz_ads_tech_state_kpi
ORDER BY period_year, period_month;

-- ================================================================
-- 查询卡片: 里程碑KPI
-- 用途: 里程碑节点完成情况统计，包括按时完成、逾期完成、高风险数等
-- 对应大屏: Screen 2（执行看板）
-- 数据表: biz_ads_project_milestone_kpi
-- ================================================================

SELECT
    plan_year,
    plan_quarter,
    plan_month,
    milestone_on_time_cnt,
    milestone_overdue_completed_cnt,
    milestone_pending_cnt,
    milestone_incomplete_cnt,
    milestone_completion_rate,
    high_risk_cnt,
    mid_risk_cnt,
    milestone_total_cnt,
    major_total_cnt,
    important_total_cnt
FROM biz_ads_project_milestone_kpi
ORDER BY plan_year, plan_month

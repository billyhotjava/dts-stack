-- ================================================================
-- 查询卡片: 执行域KPI总览
-- 用途: 按月统计整体执行KPI，包括完成率、按时率、逾期完成率等
-- 对应大屏: Screen 1（总览KPI）、Screen 2（执行KPI）
-- 数据表: biz_ads_project_kpi_overview
-- ================================================================

SELECT
    plan_year,
    plan_quarter,
    plan_month,
    total_cnt,
    pending_normal_cnt,
    due_cnt,
    outside_completed_cnt,
    incomplete_cnt,
    on_time_cnt,
    overdue_completed_cnt,
    completed_total_cnt,
    completion_rate,
    on_time_rate,
    overdue_completion_rate,
    abnormal_pending_cnt,
    overdue_incomplete_unchanged_cnt,
    overdue_incomplete_changed_cnt
FROM biz_ads_project_kpi_overview
ORDER BY plan_year, plan_month

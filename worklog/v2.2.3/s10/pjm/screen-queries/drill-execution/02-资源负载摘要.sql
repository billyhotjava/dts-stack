-- ================================================================
-- 查询卡片: 周度子项目汇总
-- 用途: 按周统计各子项目的进度、完成率、里程碑完成率等
-- 对应大屏: Screen 2
-- 数据表: biz_dws_week_subproject_summary
-- ================================================================

SELECT
    major_project_id,
    major_project_name,
    subproject_id,
    subproject_name,
    week_start_date,
    plan_iso_week,
    total_nodes,
    completed_nodes,
    overdue_open_nodes,
    high_risk_nodes,
    milestone_nodes,
    milestone_completed_nodes,
    avg_health_score,
    avg_delay_days,
    max_delay_days,
    completion_rate,
    milestone_completion_rate
FROM biz_dws_week_subproject_summary
ORDER BY plan_iso_week DESC, major_project_id, subproject_id

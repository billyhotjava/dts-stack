-- ================================================================
-- 查询卡片: 重大专项总览
-- 用途: 项目级汇总，包括节点数、完成数、逾期数、健康分等
-- 对应大屏: Screen 1（项目状态表）、Screen 2
-- 数据表: biz_ads_major_project_overview
-- ================================================================

SELECT
    major_project_id,
    major_project_name,
    total_nodes,
    subproject_count,
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
FROM biz_ads_major_project_overview
ORDER BY avg_health_score ASC

-- ================================================================
-- 查询卡片: 周度子项目汇总
-- 用途: 按周统计各子项目的进度、完成率、里程碑完成率等
-- 对应大屏: Screen 2
-- 数据表: biz_dws_week_subproject_summary
-- ================================================================

SELECT
    major_project_id,                       -- 项目编号
    major_project_name,                     -- 项目名称
    subproject_id,                          -- 子项目编号
    subproject_name,                        -- 子项目名称
    week_start_date,                        -- 周开始日期
    plan_iso_week,                          -- 计划ISO周
    total_nodes,                            -- 节点总数
    completed_nodes,                        -- 已完成节点数
    overdue_open_nodes,                     -- 超期未完成节点数
    high_risk_nodes,                        -- 高风险节点数
    milestone_nodes,                        -- 里程碑节点数
    milestone_completed_nodes,              -- 里程碑已完成数
    avg_health_score,                       -- 平均健康度评分
    avg_delay_days,                         -- 平均延期天数
    max_delay_days,                         -- 最大延期天数
    completion_rate,                        -- 完成率
    ROUND(completion_rate * 100, 2) || '%' AS completion_rate_pct,                        -- [显示用] 完成率
    milestone_completion_rate,  -- 里程碑完成率
    ROUND(milestone_completion_rate * 100, 2) || '%' AS milestone_completion_rate_pct               -- [显示用] 里程碑完成率
FROM biz_dws_week_subproject_summary
ORDER BY plan_iso_week DESC, major_project_id, subproject_id

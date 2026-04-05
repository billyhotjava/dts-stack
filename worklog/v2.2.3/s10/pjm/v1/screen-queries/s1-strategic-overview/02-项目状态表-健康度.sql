-- ================================================================
-- 查询卡片: 重大专项总览
-- 用途: 项目级汇总，包括节点数、完成数、逾期数、健康分等
-- 对应大屏: Screen 1（项目状态表）、Screen 2
-- 数据表: biz_ads_major_project_overview
-- ================================================================

SELECT
    major_project_id,                       -- 项目编号
    major_project_name,                     -- 项目名称
    total_nodes,                            -- 节点总数
    subproject_count,                       -- 子项目数
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
FROM biz_ads_major_project_overview
ORDER BY avg_health_score ASC

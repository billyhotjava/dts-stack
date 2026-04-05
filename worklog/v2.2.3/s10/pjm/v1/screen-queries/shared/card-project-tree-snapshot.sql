-- ================================================================
-- 查询卡片: 项目树形快照
-- 用途: 层级树状视图，用于甘特图及树形组件展示项目-子项目-节点结构
-- 对应大屏: Screen 2（甘特图）
-- 数据表: biz_ads_major_project_tree_snapshot
-- ================================================================

SELECT
    snapshot_level,                         -- 快照层级（项目/子项目/节点）
    entity_id,                              -- 实体编号
    parent_id,                              -- 父级编号
    entity_name,                            -- 实体名称
    major_project_id,                       -- 所属项目编号
    major_project_name,                     -- 所属项目名称
    subproject_id,                          -- 子项目编号
    subproject_name,                        -- 子项目名称
    total_nodes,                            -- 节点总数
    completed_nodes,                        -- 已完成节点数
    overdue_open_nodes,                     -- 超期未完成节点数
    high_risk_nodes,                        -- 高风险节点数
    avg_health_score,                       -- 平均健康度评分
    plan_start_date,                        -- 计划开始日期
    plan_end_date,                          -- 计划结束日期
    actual_end_date,                        -- 实际结束日期
    progress_rate,                          -- 进度百分比
    ROUND(progress_rate * 100, 2) || '%' AS progress_rate_pct,                          -- [显示用] 进度百分比
    sort_order,                             -- 排序序号
    node_id,                                -- 节点编号
    node_task,                              -- 节点任务名称
    node_type,                              -- 节点类型
    risk_level,                             -- 风险等级
    delay_days                              -- 延期天数
FROM biz_ads_major_project_tree_snapshot
ORDER BY major_project_id, sort_order

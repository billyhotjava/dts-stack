-- ================================================================
-- 查询卡片: 项目树形快照
-- 用途: 层级树状视图，用于甘特图及树形组件展示项目-子项目-节点结构
-- 对应大屏: Screen 2（甘特图）
-- 数据表: biz_ads_major_project_tree_snapshot
-- ================================================================

SELECT
    snapshot_level,
    entity_id,
    parent_id,
    entity_name,
    major_project_id,
    major_project_name,
    subproject_id,
    subproject_name,
    total_nodes,
    completed_nodes,
    overdue_open_nodes,
    high_risk_nodes,
    avg_health_score,
    plan_start_date,
    plan_end_date,
    actual_end_date,
    progress_rate,
    sort_order,
    node_id,
    node_task,
    node_type,
    risk_level,
    delay_days
FROM biz_ads_major_project_tree_snapshot
ORDER BY major_project_id, sort_order

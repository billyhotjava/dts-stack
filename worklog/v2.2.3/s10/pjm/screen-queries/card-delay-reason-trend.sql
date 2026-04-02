-- ================================================================
-- 查询卡片: 延期原因趋势
-- 用途: 按周统计延期原因分类、延期节点数、高风险节点数等
-- 对应大屏: Screen 1（延期TOP5）、Screen 2（延期表格）
-- 数据表: biz_ads_delay_reason_trend
-- ================================================================

SELECT
    major_project_id,
    major_project_name,
    dept,
    week_start_date,
    plan_iso_week,
    delay_reason_category,
    delay_reason_label,
    delayed_node_count,
    high_risk_node_count,
    delayed_subproject_count
FROM biz_ads_delay_reason_trend
ORDER BY plan_iso_week DESC, delayed_node_count DESC

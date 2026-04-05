-- ================================================================
-- 查询卡片: 延期原因趋势
-- 用途: 按周统计延期原因分类、延期节点数、高风险节点数等
-- 对应大屏: Screen 1（延期TOP5）、Screen 2（延期表格）
-- 数据表: biz_ads_delay_reason_trend
-- ================================================================

SELECT
    major_project_id,                       -- 项目编号
    major_project_name,                     -- 项目名称
    dept,                                   -- 责任单位
    week_start_date,                        -- 周开始日期
    plan_iso_week,                          -- 计划ISO周
    delay_reason_category,                  -- 延期原因分类
    delay_reason_label,                     -- 延期原因标签
    delayed_node_count,                     -- 延期节点数
    high_risk_node_count,                   -- 高风险节点数
    delayed_subproject_count                -- 延期子项目数
FROM biz_ads_delay_reason_trend
ORDER BY plan_iso_week DESC, delayed_node_count DESC

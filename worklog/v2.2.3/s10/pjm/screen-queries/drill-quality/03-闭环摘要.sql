-- ================================================================
-- 查询卡片: 质量域KPI总览
-- 用途: 按月统计质量KPI，包括问题数、归零完成率、闭环率等
-- 对应大屏: Screen 1（质量总览）、Screen 3（质量看板KPI、图表）
-- 数据表: biz_ads_quality_kpi
-- ================================================================

SELECT
    period_year,                            -- 统计年度
    period_month,                           -- 统计月份
    total_issue_cnt,                        -- 质量问题总数
    open_issue_cnt,                         -- 未归零问题数
    closed_cnt,                             -- 已归零问题数
    closure_rate,                           -- 归零完成率
    ROUND(closure_rate * 100, 2) || '%' AS closure_rate_pct,                           -- [显示用] 归零完成率
    has_zero_plan_cnt,                      -- 有归零计划数
    zero_plan_synced_cnt,                   -- 归零计划已同步数
    cat_design,                             -- 设计类问题数
    cat_process,                            -- 工艺类问题数
    cat_management,                         -- 管理类问题数
    cat_component,                          -- 元器件类问题数
    cat_operation,                          -- 操作类问题数
    cat_outsource,                          -- 外协类问题数
    cat_software,                           -- 软件类问题数
    cat_other,                              -- 其他类问题数
    measure_count                           -- 关联措施数
FROM biz_ads_quality_kpi
ORDER BY period_year, period_month;

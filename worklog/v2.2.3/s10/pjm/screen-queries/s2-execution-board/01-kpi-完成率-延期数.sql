-- ================================================================
-- 查询卡片: 执行域KPI总览
-- 用途: 按月统计整体执行KPI，包括完成率、按时率、逾期完成率等
-- 对应大屏: Screen 1（总览KPI）、Screen 2（执行KPI）
-- 数据表: biz_ads_project_kpi_overview
-- ================================================================

SELECT
    plan_year,                              -- 计划年度
    plan_quarter,                           -- 计划季度
    plan_month,                             -- 计划月份
    total_cnt,                              -- 本周期节点总数
    pending_normal_cnt,                     -- 正常待完成数
    due_cnt,                                -- 已到时间节点数（总数-正常待完成）
    outside_completed_cnt,                  -- 本周期以外完成数
    incomplete_cnt,                         -- 未完成总数
    on_time_cnt,                            -- 按时完成数
    overdue_completed_cnt,                  -- 超期完成数
    completed_total_cnt,                    -- 完成总数（按时+超期+以外）
    completion_rate,                        -- 完成百分比
    ROUND(completion_rate * 100, 2) || '%' AS completion_rate_pct,                        -- [显示用] 完成百分比
    on_time_rate,                           -- 按时完成百分比
    ROUND(on_time_rate * 100, 2) || '%' AS on_time_rate_pct,                           -- [显示用] 按时完成百分比
    overdue_completion_rate,                -- 超期完成百分比
    ROUND(overdue_completion_rate * 100, 2) || '%' AS overdue_completion_rate_pct,                -- [显示用] 超期完成百分比
    abnormal_pending_cnt,                   -- 不正常待变更数
    overdue_incomplete_unchanged_cnt,       -- 超期未完成未变更数
    overdue_incomplete_changed_cnt          -- 超期未完成已变更数
FROM biz_ads_project_kpi_overview
ORDER BY plan_year, plan_month

-- ================================================================
-- 查询卡片: 非一般节点异常KPI
-- 用途: 里程碑/重要节点的异常待办、逾期未完成、逾期完成等异常指标统计
-- 对应大屏: Screen 2
-- 数据表: biz_ads_project_non_general_kpi
-- ================================================================

SELECT
    plan_year,                              -- 计划年度
    plan_quarter,                           -- 计划季度
    plan_month,                             -- 计划月份
    abnormal_pending_cnt,                   -- 不正常待变更节点数（除一般节点）
    overdue_incomplete_unchanged_cnt,       -- 超期未完成未变更数（除一般）
    overdue_incomplete_changed_cnt,         -- 超期未完成已变更数（除一般）
    overdue_completed_unchanged_cnt,        -- 超期已完成未变更数（除一般）
    abnormal_rate,                          -- 不正常待变更百分比
    overdue_rate                            -- 节点超期百分比
FROM biz_ads_project_non_general_kpi
ORDER BY plan_year, plan_month

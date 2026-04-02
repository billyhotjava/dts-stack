-- ================================================================
-- 查询卡片: 非一般节点异常KPI
-- 用途: 里程碑/重要节点的异常待办、逾期未完成、逾期完成等异常指标统计
-- 对应大屏: Screen 2
-- 数据表: biz_ads_project_non_general_kpi
-- ================================================================

SELECT
    plan_year,
    plan_quarter,
    plan_month,
    abnormal_pending_cnt,
    overdue_incomplete_unchanged_cnt,
    overdue_incomplete_changed_cnt,
    overdue_completed_unchanged_cnt,
    abnormal_rate,
    overdue_rate
FROM biz_ads_project_non_general_kpi
ORDER BY plan_year, plan_month

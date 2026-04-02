-- ================================================================
-- 查询卡片: 里程碑KPI
-- 用途: 里程碑节点完成情况统计，包括按时完成、逾期完成、高风险数等
-- 对应大屏: Screen 2（执行看板）
-- 数据表: biz_ads_project_milestone_kpi
-- ================================================================

SELECT
    plan_year,                              -- 计划年度
    plan_quarter,                           -- 计划季度
    plan_month,                             -- 计划月份
    milestone_on_time_cnt,                  -- 里程碑按时完成数
    milestone_overdue_completed_cnt,        -- 里程碑超期完成数
    milestone_pending_cnt,                  -- 里程碑正常待完成数
    milestone_incomplete_cnt,               -- 里程碑未完成数
    milestone_completion_rate,              -- 里程碑完成百分比
    high_risk_cnt,                          -- 高风险节点数
    mid_risk_cnt,                           -- 中风险节点数
    milestone_total_cnt,                    -- 里程碑节点总数
    major_total_cnt,                        -- 重大节点总数
    important_total_cnt                     -- 重要节点总数
FROM biz_ads_project_milestone_kpi
ORDER BY plan_year, plan_month

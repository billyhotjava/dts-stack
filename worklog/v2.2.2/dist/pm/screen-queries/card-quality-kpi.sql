-- ================================================================
-- 查询卡片: 质量域KPI总览
-- 用途: 按月统计质量KPI，包括问题数、归零完成率、闭环率等
-- 对应大屏: Screen 1（质量总览）、Screen 3（质量看板KPI、图表）
-- 数据表: biz_ads_quality_kpi
-- ================================================================

SELECT
    period_year,
    period_month,
    total_issue_cnt,
    new_issue_cnt,
    open_issue_cnt,
    tech_zero_cnt,
    mgmt_zero_cnt,
    both_zero_cnt,
    zero_completed_cnt,
    closed_cnt,
    closure_rate,
    zero_completion_rate,
    no_zero_plan_cnt,
    cat_design_cnt,
    cat_process_cnt,
    cat_management_cnt,
    cat_component_cnt,
    cat_operation_cnt,
    cat_outsource_cnt,
    cat_software_cnt,
    cat_other_cnt,
    has_measure_cnt,
    measure_coverage_rate
FROM biz_ads_quality_kpi
ORDER BY period_year, period_month

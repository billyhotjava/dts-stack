-- ================================================================
-- 查询卡片: 质量域项目/部门汇总
-- 用途: 按项目和部门统计质量汇总，用于项目排名图表
-- 对应大屏: Screen 3（项目排名图表）
-- 数据表: biz_dws_quality_period_summary
-- ================================================================

SELECT
    period_year,
    period_quarter,
    period_month,
    project_no,
    dept,
    total_issue_cnt,
    new_issue_cnt,
    open_issue_cnt,
    tech_zero_cnt,
    mgmt_zero_cnt,
    both_zero_cnt,
    zero_completed_cnt,
    closed_cnt,
    closure_rate,
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
FROM biz_dws_quality_period_summary
ORDER BY period_year, period_month, project_no

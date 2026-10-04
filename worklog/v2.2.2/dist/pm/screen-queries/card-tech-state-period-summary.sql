-- ================================================================
-- 查询卡片: 技术状态域项目/部门汇总
-- 用途: 按项目和部门统计技术状态汇总，用于项目维度分析
-- 对应大屏: Screen 4（技术状态看板）
-- 数据表: biz_dws_tech_state_period_summary
-- ================================================================

SELECT
    period_year,
    period_quarter,
    period_month,
    project_no,
    dept,
    total_change_cnt,
    cat_i_cnt,
    cat_ii_cnt,
    cat_iii_cnt,
    signed_cnt,
    reviewing_cnt,
    unsigned_cnt,
    signature_completion_rate,
    closed_cnt,
    open_cnt,
    reform_done_cnt,
    reform_na_cnt,
    reform_pending_cnt,
    file_unsigned_i_ii_cnt,
    file_unsigned_iii_cnt,
    file_signed_cnt,
    change_unsigned_i_cnt,
    change_unsigned_ii_cnt,
    change_unsigned_iii_cnt,
    change_signed_cnt,
    unreformed_i_ii_cnt,
    reformed_i_ii_cnt,
    reform_na_iii_cnt,
    has_measure_cnt,
    measure_coverage_rate
FROM biz_dws_tech_state_period_summary
ORDER BY period_year, period_month, project_no

-- ================================================================
-- 查询卡片: 技术状态域KPI总览
-- 用途: 按月统计技术状态KPI，包括更改数、签署完成率、整改情况等
-- 对应大屏: Screen 1（总览）、Screen 4（技术状态看板）
-- 数据表: biz_ads_tech_state_kpi
-- ================================================================

SELECT
    period_year,
    period_month,
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
FROM biz_ads_tech_state_kpi
ORDER BY period_year, period_month

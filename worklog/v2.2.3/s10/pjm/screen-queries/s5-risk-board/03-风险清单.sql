-- ================================================================
-- 查询卡片: 风险信息明细
-- 用途: 展示所有风险记录，含风险等级、释放状态、滞留天数
-- 对应大屏: Screen 8（风险明细表）
-- 数据表: biz_dwd_risk_info
-- ================================================================

SELECT
    risk_id,
    project_no,
    risk_name,
    subsystem,
    belonging_unit,
    risk_description,
    risk_phase,
    risk_category,
    risk_level,
    impact_scope,
    response_measure,
    monthly_control_plan,
    weekly_release_plan,
    release_plan_synced,
    new_plan_count,
    progress_situation,
    response_owner,
    control_owner,
    dept,
    risk_status,
    remark,
    filled_by,
    risk_submit_week,
    final_release_week,
    progress_stat_week,
    risk_release_week,
    last_update_week,
    risk_rank,
    is_released,
    risk_submit_date,
    final_release_date,
    progress_stat_date,
    risk_release_date,
    last_update_time,
    submit_year,
    submit_quarter,
    submit_month,
    pending_days,
    source_table,
    etl_time
FROM biz_dwd_risk_info
ORDER BY risk_submit_date DESC NULLS LAST;

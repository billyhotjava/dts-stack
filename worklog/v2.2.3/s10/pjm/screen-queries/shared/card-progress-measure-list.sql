-- ================================================================
-- 查询卡片: 进度措施明细
-- 用途: 展示所有进度措施记录，含跟进人、闭环状态、交付物信息
-- 对应大屏: Screen 12（进度措施明细表）
-- 数据表: biz_dwd_progress_measure
-- ================================================================

SELECT
    measure_id,
    project_no,
    subsystem,
    node_task,
    completion_status,
    measure_category,
    measure_title,
    follow_up_person,
    main_recipient,
    cc_recipient,
    closure_status,
    closure_deliverable_type,
    closure_deliverable,
    risk_content,
    remark,
    filled_by,
    plan_date,
    follow_up_date,
    final_closure_date,
    last_update_time,
    plan_week,
    follow_up_week,
    final_closure_week,
    last_update_week,
    plan_year,
    plan_quarter,
    plan_month,
    follow_up_year,
    follow_up_month,
    plan_to_followup_days,
    followup_to_closure_days,
    source_table,
    etl_time
FROM biz_dwd_progress_measure
ORDER BY follow_up_date DESC NULLS LAST;

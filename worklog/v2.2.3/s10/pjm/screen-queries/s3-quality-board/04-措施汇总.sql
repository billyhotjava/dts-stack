-- ================================================================
-- 查询卡片: 质量措施明细
-- 用途: 展示质量问题跟进措施记录，含闭环状态、交付物信息
-- 对应大屏: Screen 2（质量措施跟进明细表）
-- 数据表: biz_dwd_quality_measure
-- ================================================================

SELECT
    measure_id,
    project_no,
    subsystem,
    issue_name,
    dept,
    team_leader,
    dept_leader,
    issue_summary,
    issue_category,
    zero_plan,
    zero_plan_synced,
    status,
    current_progress,
    project_manager,
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
    new_plan_count,
    issue_date,
    follow_up_date,
    final_closure_date,
    last_update_time,
    issue_week,
    follow_up_week,
    final_closure_week,
    last_update_week,
    is_closed,
    source_table,
    etl_time
FROM biz_dwd_quality_measure
ORDER BY follow_up_date DESC NULLS LAST;

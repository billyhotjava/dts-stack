-- ================================================================
-- 查询卡片: 技术状态措施明细
-- 用途: 展示技术状态变更跟进措施记录，含闭环状态、交付物信息
-- 对应大屏: Screen 5（技术状态措施跟进明细表）
-- 数据表: biz_dwd_tech_state_measure
-- ================================================================

SELECT
    measure_id,
    project_no,
    tech_state_name,
    change_item,
    owner,
    dept,
    dept_leader,
    completion_signature,
    change_reason,
    change_category,
    plan_synced,
    affected_files,
    affected_objects,
    file_signature_status,
    reform_status,
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
    change_submit_time,
    signature_closure_date,
    plan_file_closure_date,
    plan_reform_date,
    follow_up_date,
    final_closure_date,
    last_update_time,
    change_submit_week,
    signature_closure_week,
    plan_file_closure_week,
    plan_reform_week,
    follow_up_week,
    final_closure_week,
    last_update_week,
    is_closed,
    source_table,
    etl_time
FROM biz_dwd_tech_state_measure
ORDER BY follow_up_date DESC NULLS LAST;

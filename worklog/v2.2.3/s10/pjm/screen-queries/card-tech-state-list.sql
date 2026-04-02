-- ================================================================
-- 查询卡片: 技术状态变更明细
-- 用途: 展示所有技术状态变更记录，含签署、整改落实状态
-- 对应大屏: Screen 5（技术状态明细表）
-- 数据表: biz_dwd_tech_state
-- ================================================================

SELECT
    tech_state_id,
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
    review_situation,
    affected_files,
    affected_objects,
    file_signature_status,
    reform_status,
    project_manager,
    filled_by,
    remark,
    new_plan_count,
    change_submit_time,
    signature_closure_date,
    plan_file_closure_date,
    plan_reform_date,
    file_signature_date,
    reform_date,
    last_update_time,
    change_submit_week,
    signature_closure_week,
    plan_file_closure_week,
    plan_reform_week,
    file_signature_week,
    reform_week,
    last_update_week,
    is_signature_completed,
    is_file_signed,
    is_reformed,
    source_table,
    etl_time
FROM biz_dwd_tech_state
ORDER BY change_submit_time DESC NULLS LAST;

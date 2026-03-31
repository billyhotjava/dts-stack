-- ================================================================
-- 查询卡片: 技术状态更改明细列表
-- 用途: 技术状态DWD明细数据，用于明细表格展示
-- 对应大屏: Screen 4（明细表格）
-- 数据表: biz_dwd_tech_state
-- ================================================================

SELECT
    tech_state_id,
    project_no,
    tech_state_name,
    change_item,
    file_signature_status,
    completion_signature,
    reform_status,
    closure_status,
    dept,
    subsystem,
    filled_by,
    remark,
    change_category,
    change_severity_rank,
    is_submitted,
    is_reviewed,
    is_signed,
    is_closed,
    is_signature_completed,
    is_reform_done,
    is_reform_not_applicable,
    change_submit_date,
    last_update_time,
    submit_year,
    submit_quarter,
    submit_month,
    source_table,
    etl_time
FROM biz_dwd_tech_state
ORDER BY change_submit_date DESC NULLS LAST

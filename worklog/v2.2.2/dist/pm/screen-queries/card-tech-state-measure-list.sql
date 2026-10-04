-- ================================================================
-- 查询卡片: 技术状态措施明细列表
-- 用途: 技术状态措施DWD明细数据，用于措施表格展示
-- 对应大屏: Screen 4（措施表格）
-- 数据表: biz_dwd_tech_state_measure
-- ================================================================

SELECT
    measure_id,
    tech_state_id_approx,
    project_no,
    tech_state_name,
    measure_content,
    measure_status,
    responsible_person,
    filled_by,
    deadline,
    actual_complete_date,
    last_update_time,
    source_table,
    etl_time
FROM biz_dwd_tech_state_measure
ORDER BY deadline DESC NULLS LAST

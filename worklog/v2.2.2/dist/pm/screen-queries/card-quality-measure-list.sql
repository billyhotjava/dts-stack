-- ================================================================
-- 查询卡片: 质量措施明细列表
-- 用途: 质量措施DWD明细数据，用于措施表格展示
-- 对应大屏: Screen 3（措施表格）
-- 数据表: biz_dwd_quality_measure
-- ================================================================

SELECT
    measure_id,
    issue_id_approx,
    project_no,
    issue_name,
    measure_content,
    measure_status,
    responsible_person,
    filled_by,
    deadline,
    actual_complete_date,
    last_update_time,
    source_table,
    etl_time
FROM biz_dwd_quality_measure
ORDER BY deadline DESC

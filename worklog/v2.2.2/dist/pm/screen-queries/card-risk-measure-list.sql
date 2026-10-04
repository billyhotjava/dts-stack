-- ================================================================
-- 查询卡片: 风险应对措施列表
-- 用途: 风险应对措施明细，展示措施内容、责任人、截止日期等
-- 对应大屏: Screen 6（风险看板）
-- 数据表: biz_dwd_risk_measure
-- ================================================================

SELECT
    measure_id,
    risk_id_approx,
    project_no,
    risk_name,
    measure_content,
    measure_status,
    responsible_person,
    closure_deliverable,
    filled_by,
    deadline,
    actual_complete_date,
    last_update_time,
    source_table,
    etl_time
FROM biz_dwd_risk_measure
ORDER BY deadline DESC NULLS LAST

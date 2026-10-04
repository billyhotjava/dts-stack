-- ================================================================
-- 查询卡片: 风险明细列表
-- 用途: 风险登记明细，展示各项目风险等级、状态、滞留天数等
-- 对应大屏: Screen 6（风险看板）
-- 数据表: biz_dwd_risk_info
-- ================================================================

SELECT
    risk_id,
    project_no,
    risk_name,
    risk_level,
    risk_content,
    impact_scope,
    closure_status,
    response_measure,
    dept,
    subsystem,
    owner,
    filled_by,
    remark,
    risk_rank,
    is_closed,
    risk_submit_date,
    last_update_time,
    submit_year,
    submit_quarter,
    submit_month,
    pending_days,
    source_table,
    etl_time
FROM biz_dwd_risk_info
ORDER BY risk_submit_date DESC NULLS LAST, risk_rank DESC

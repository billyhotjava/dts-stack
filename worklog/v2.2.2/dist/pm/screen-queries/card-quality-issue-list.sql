-- ================================================================
-- 查询卡片: 质量问题明细列表
-- 用途: 质量问题DWD明细数据，用于明细表格展示
-- 对应大屏: Screen 3（明细表格）
-- 数据表: biz_dwd_quality_issue
-- ================================================================

SELECT
    issue_id,
    project_no,
    issue_name,
    issue_category,
    status,
    closure_status,
    zero_plan,
    dept,
    subsystem,
    owner,
    filled_by,
    remark,
    is_zero_completed,
    is_tech_zero,
    is_mgmt_zero,
    is_both_zero,
    is_closed,
    has_zero_plan,
    issue_date,
    last_update_time,
    issue_year,
    issue_quarter,
    issue_month,
    pending_days,
    source_table,
    etl_time
FROM biz_dwd_quality_issue
ORDER BY issue_date DESC

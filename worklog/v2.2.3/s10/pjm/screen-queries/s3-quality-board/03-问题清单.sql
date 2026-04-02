-- ================================================================
-- 查询卡片: 质量问题明细
-- 用途: 展示所有质量问题记录，含归零计划、闭环状态、滞留天数
-- 对应大屏: Screen 2（质量问题明细表）
-- 数据表: biz_dwd_quality_issue
-- ================================================================

SELECT
    issue_id,
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
    filled_by,
    new_plan_count,
    issue_date,
    zero_complete_date,
    last_update_time,
    issue_week,
    zero_complete_week,
    last_update_week,
    is_closed,
    has_zero_plan,
    issue_year,
    issue_quarter,
    issue_month,
    pending_days,
    source_table,
    etl_time
FROM biz_dwd_quality_issue
ORDER BY issue_date DESC NULLS LAST;

-- ================================================================
-- 查询卡片: 质量问题明细
-- 用途: 展示所有质量问题记录，含归零计划、闭环状态、滞留天数
-- 对应大屏: Screen 2（质量问题明细表）
-- 数据表: biz_dwd_quality_issue
-- ================================================================

SELECT
    issue_id,                               -- 问题编号
    project_no,                             -- 项目编号
    subsystem,                              -- 分系统
    issue_name,                             -- 问题名称
    dept,                                   -- 责任单位
    team_leader,                            -- 组长
    dept_leader,                            -- 部门负责人
    issue_summary,                          -- 问题摘要
    issue_category,                         -- 问题分类
    zero_plan,                              -- 归零计划
    zero_plan_synced,                       -- 归零计划同步情况
    status,                                 -- 状态
    current_progress,                       -- 当前进展
    project_manager,                        -- 项目经理
    filled_by,                              -- 填报人
    new_plan_count,                         -- 新增计划数
    issue_date,                             -- 问题发生日期
    zero_complete_date,                     -- 归零完成日期
    last_update_time,                       -- 最后更新时间
    issue_week,                             -- 问题发生周
    zero_complete_week,                     -- 归零完成周
    last_update_week,                       -- 最后更新周
    is_closed,                              -- 是否已闭环
    has_zero_plan,                          -- 是否有归零计划
    issue_year,                             -- 问题年度
    issue_quarter,                          -- 问题季度
    issue_month,                            -- 问题月份
    pending_days,                           -- 滞留天数
    source_table,                           -- 来源表
    etl_time                                -- ETL处理时间
FROM biz_dwd_quality_issue
ORDER BY issue_date DESC NULLS LAST;

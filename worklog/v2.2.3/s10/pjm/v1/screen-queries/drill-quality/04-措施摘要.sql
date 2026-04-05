-- ================================================================
-- 查询卡片: 质量措施明细
-- 用途: 展示质量问题跟进措施记录，含闭环状态、交付物信息
-- 对应大屏: Screen 2（质量措施跟进明细表）
-- 数据表: biz_dwd_quality_measure
-- ================================================================

SELECT
    measure_id,                             -- 措施编号
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
    measure_category,                       -- 措施分类
    measure_title,                          -- 措施标题
    follow_up_person,                       -- 跟进人
    main_recipient,                         -- 主送人
    cc_recipient,                           -- 抄送人
    closure_status,                         -- 闭环状态
    closure_deliverable_type,               -- 闭环交付物类型
    closure_deliverable,                    -- 闭环交付物
    risk_content,                           -- 风险内容
    remark,                                 -- 备注
    filled_by,                              -- 填报人
    new_plan_count,                         -- 新增计划数
    issue_date,                             -- 问题发生日期
    follow_up_date,                         -- 跟进日期
    final_closure_date,                     -- 最终闭环日期
    last_update_time,                       -- 最后更新时间
    issue_week,                             -- 问题发生周
    follow_up_week,                         -- 跟进周
    final_closure_week,                     -- 最终闭环周
    last_update_week,                       -- 最后更新周
    is_closed,                              -- 是否已闭环
    source_table,                           -- 来源表
    etl_time                                -- ETL处理时间
FROM biz_dwd_quality_measure
ORDER BY follow_up_date DESC NULLS LAST;

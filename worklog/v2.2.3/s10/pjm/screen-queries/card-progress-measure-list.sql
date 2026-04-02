-- ================================================================
-- 查询卡片: 进度措施明细
-- 用途: 展示所有进度措施记录，含跟进人、闭环状态、交付物信息
-- 对应大屏: Screen 12（进度措施明细表）
-- 数据表: biz_dwd_progress_measure
-- ================================================================

SELECT
    measure_id,                             -- 措施编号
    project_no,                             -- 项目编号
    subsystem,                              -- 分系统
    node_task,                              -- 节点任务
    completion_status,                      -- 完成状态
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
    plan_date,                              -- 计划日期
    follow_up_date,                         -- 跟进日期
    final_closure_date,                     -- 最终闭环日期
    last_update_time,                       -- 最后更新时间
    plan_week,                              -- 计划周
    follow_up_week,                         -- 跟进周
    final_closure_week,                     -- 最终闭环周
    last_update_week,                       -- 最后更新周
    plan_year,                              -- 计划年度
    plan_quarter,                           -- 计划季度
    plan_month,                             -- 计划月份
    follow_up_year,                         -- 跟进年度
    follow_up_month,                        -- 跟进月份
    plan_to_followup_days,                  -- 计划到跟进天数
    followup_to_closure_days,               -- 跟进到闭环天数
    source_table,                           -- 来源表
    etl_time                                -- ETL处理时间
FROM biz_dwd_progress_measure
ORDER BY follow_up_date DESC NULLS LAST;

-- ================================================================
-- 查询卡片: 风险措施明细
-- 用途: 展示风险跟进措施记录，含闭环状态、交付物信息
-- 对应大屏: Screen 8（风险措施跟进明细表）
-- 数据表: biz_dwd_risk_measure
-- ================================================================

SELECT
    risk_measure_id,                        -- 风险措施编号
    project_no,                             -- 项目编号
    risk_name,                              -- 风险名称
    subsystem,                              -- 分系统
    belonging_unit,                         -- 所属单位
    risk_description,                       -- 风险描述
    risk_phase,                             -- 风险阶段
    risk_category,                          -- 风险分类
    risk_level,                             -- 风险等级
    impact_scope,                           -- 影响范围
    response_measure,                       -- 应对措施
    monthly_control_plan,                   -- 月度管控计划
    weekly_release_plan,                    -- 周释放计划
    release_plan_synced,                    -- 释放计划同步情况
    new_plan_count,                         -- 新增计划数
    progress_situation,                     -- 进展情况
    response_owner,                         -- 应对责任人
    control_owner,                          -- 管控责任人
    dept,                                   -- 责任单位
    risk_status,                            -- 风险状态
    project_manager,                        -- 项目经理
    remark,                                 -- 备注
    filled_by,                              -- 填报人
    measure_category,                       -- 措施分类
    measure_title,                          -- 措施标题
    follow_up_person,                       -- 跟进人
    main_recipient,                         -- 主送人
    cc_recipient,                           -- 抄送人
    closure_status,                         -- 闭环状态
    closure_deliverable_type,               -- 闭环交付物类型
    closure_deliverable,                    -- 闭环交付物
    risk_content,                           -- 风险内容
    risk_submit_week,                       -- 风险提交周
    progress_stat_week,                     -- 进展统计周
    follow_up_week,                         -- 跟进周
    final_closure_week,                     -- 最终闭环周
    last_update_week,                       -- 最后更新周
    risk_rank,                              -- 风险排名
    is_closed,                              -- 是否已闭环
    risk_submit_date,                       -- 风险提交日期
    final_release_date,                     -- 最终释放日期
    progress_stat_date,                     -- 进展统计日期
    follow_up_date,                         -- 跟进日期
    final_closure_date,                     -- 最终闭环日期
    last_update_time,                       -- 最后更新时间
    submit_year,                            -- 提交年度
    submit_quarter,                         -- 提交季度
    submit_month,                           -- 提交月份
    pending_days,                           -- 滞留天数
    source_table,                           -- 来源表
    etl_time                                -- ETL处理时间
FROM biz_dwd_risk_measure
ORDER BY follow_up_date DESC NULLS LAST;

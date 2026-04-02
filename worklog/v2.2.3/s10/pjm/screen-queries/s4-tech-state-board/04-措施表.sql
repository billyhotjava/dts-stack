-- ================================================================
-- 查询卡片: 技术状态措施明细
-- 用途: 展示技术状态变更跟进措施记录，含闭环状态、交付物信息
-- 对应大屏: Screen 5（技术状态措施跟进明细表）
-- 数据表: biz_dwd_tech_state_measure
-- ================================================================

SELECT
    measure_id,                             -- 措施编号
    project_no,                             -- 项目编号
    tech_state_name,                        -- 技术状态名称
    change_item,                            -- 变更项目
    owner,                                  -- 负责人
    dept,                                   -- 责任单位
    dept_leader,                            -- 部门负责人
    completion_signature,                   -- 完成签署情况
    change_reason,                          -- 变更原因
    change_category,                        -- 变更类别
    plan_synced,                            -- 计划同步情况
    affected_files,                         -- 影响文件
    affected_objects,                       -- 影响对象
    file_signature_status,                  -- 文件签署状态
    reform_status,                          -- 整改落实状态
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
    change_submit_time,                     -- 变更提交时间
    signature_closure_date,                 -- 签署闭环日期
    plan_file_closure_date,                 -- 计划文件闭环日期
    plan_reform_date,                       -- 计划整改日期
    follow_up_date,                         -- 跟进日期
    final_closure_date,                     -- 最终闭环日期
    last_update_time,                       -- 最后更新时间
    change_submit_week,                     -- 变更提交周
    signature_closure_week,                 -- 签署闭环周
    plan_file_closure_week,                 -- 计划文件闭环周
    plan_reform_week,                       -- 计划整改周
    follow_up_week,                         -- 跟进周
    final_closure_week,                     -- 最终闭环周
    last_update_week,                       -- 最后更新周
    is_closed,                              -- 是否已闭环
    source_table,                           -- 来源表
    etl_time                                -- ETL处理时间
FROM biz_dwd_tech_state_measure
ORDER BY follow_up_date DESC NULLS LAST;

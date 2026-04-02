-- ================================================================
-- 查询卡片: 技术状态变更明细
-- 用途: 展示所有技术状态变更记录，含签署、整改落实状态
-- 对应大屏: Screen 5（技术状态明细表）
-- 数据表: biz_dwd_tech_state
-- ================================================================

SELECT
    tech_state_id,                          -- 技术状态编号
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
    review_situation,                       -- 评审情况
    affected_files,                         -- 影响文件
    affected_objects,                       -- 影响对象
    file_signature_status,                  -- 文件签署状态
    reform_status,                          -- 整改落实状态
    project_manager,                        -- 项目经理
    filled_by,                              -- 填报人
    remark,                                 -- 备注
    new_plan_count,                         -- 新增计划数
    change_submit_time,                     -- 变更提交时间
    signature_closure_date,                 -- 签署闭环日期
    plan_file_closure_date,                 -- 计划文件闭环日期
    plan_reform_date,                       -- 计划整改日期
    file_signature_date,                    -- 文件签署日期
    reform_date,                            -- 整改日期
    last_update_time,                       -- 最后更新时间
    change_submit_week,                     -- 变更提交周
    signature_closure_week,                 -- 签署闭环周
    plan_file_closure_week,                 -- 计划文件闭环周
    plan_reform_week,                       -- 计划整改周
    file_signature_week,                    -- 文件签署周
    reform_week,                            -- 整改周
    last_update_week,                       -- 最后更新周
    is_signature_completed,                 -- 是否签署完成
    is_file_signed,                         -- 是否文件已签署
    is_reformed,                            -- 是否整改完成
    source_table,                           -- 来源表
    etl_time                                -- ETL处理时间
FROM biz_dwd_tech_state
ORDER BY change_submit_time DESC NULLS LAST;

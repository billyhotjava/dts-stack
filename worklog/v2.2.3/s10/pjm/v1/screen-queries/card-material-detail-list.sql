-- ================================================================
-- 查询卡片: 物资明细
-- 用途: 展示所有物资记录，含供应商、交付日期、风险等级、延迟天数
-- 对应大屏: Screen 11（物资明细表）
-- 数据表: biz_dwd_material_info
-- ================================================================

SELECT
    material_id,                            -- 物资编号
    project_no,                             -- 项目编号
    subsystem,                              -- 分系统
    pbs_no,                                 -- PBS编号
    pbs_name,                               -- PBS名称
    self_or_outsource,                      -- 自研或外协
    supplier_name,                          -- 供应商名称
    is_long_cycle_raw,                      -- 长周期原始标记
    dept_owner,                             -- 责任部门
    control_dept_owner,                     -- 管控部门
    weekly_progress,                        -- 周进展
    affects_major_node,                     -- 是否影响重大节点
    risk_level,                             -- 风险等级
    risk_content,                           -- 风险内容
    delay_impact,                           -- 延迟影响
    remark,                                 -- 备注
    contract_negotiation_week,              -- 合同洽谈周
    contract_delivery_week,                 -- 合同交付周
    actual_delivery_week,                   -- 实际交付周
    plan_inspect_week,                      -- 计划验收周
    complete_inspect_week,                  -- 完成验收周
    install_week,                           -- 安装周
    last_update_week,                       -- 最后更新周
    is_long_cycle,                          -- 是否长周期
    is_self_developed,                      -- 是否自研
    risk_rank,                              -- 风险排名
    contract_negotiation_date,              -- 合同洽谈日期
    contract_delivery_date,                 -- 合同交付日期
    actual_delivery_date,                   -- 实际交付日期
    plan_inspect_date,                      -- 计划验收日期
    complete_inspect_date,                  -- 完成验收日期
    install_date,                           -- 安装日期
    last_update_time,                       -- 最后更新时间
    delivery_delay_days,                    -- 交付延迟天数
    source_table,                           -- 来源表
    etl_time                                -- ETL处理时间
FROM biz_dwd_material_info
ORDER BY project_no, pbs_no;

-- ================================================================
-- 查询卡片: 物资明细
-- 用途: 展示所有物资记录，含供应商、交付日期、风险等级、延迟天数
-- 对应大屏: Screen 11（物资明细表）
-- 数据表: biz_dwd_material_info
-- ================================================================

SELECT
    material_id,
    project_no,
    subsystem,
    pbs_no,
    pbs_name,
    self_or_outsource,
    supplier_name,
    is_long_cycle_raw,
    dept_owner,
    control_dept_owner,
    weekly_progress,
    affects_major_node,
    risk_level,
    risk_content,
    delay_impact,
    remark,
    contract_negotiation_week,
    contract_delivery_week,
    actual_delivery_week,
    plan_inspect_week,
    complete_inspect_week,
    install_week,
    last_update_week,
    is_long_cycle,
    is_self_developed,
    risk_rank,
    contract_negotiation_date,
    contract_delivery_date,
    actual_delivery_date,
    plan_inspect_date,
    complete_inspect_date,
    install_date,
    last_update_time,
    delivery_delay_days,
    source_table,
    etl_time
FROM biz_dwd_material_info
ORDER BY project_no, pbs_no;

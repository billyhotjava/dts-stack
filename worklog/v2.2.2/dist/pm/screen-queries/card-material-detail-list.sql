-- ================================================================
-- 查询卡片: 物料明细表
-- 用途: 重要物料信息明细，含风险、供应商、交期
-- 对应大屏: 物料专题大屏
-- 数据表: biz_dwd_material_info
-- ================================================================

SELECT
  material_id,
  project_no,
  subsystem,
  pbs_no,
  pbs_name,
  risk_name,
  risk_description,
  self_or_outsource,
  supplier_name,
  is_long_cycle,
  belonging_unit,
  delivery_date,
  delivery_month,
  last_update_time,
  filled_by,
  remark
FROM biz_dwd_material_info
ORDER BY project_no, subsystem, pbs_no

-- ================================================================
-- 查询卡片: 物料项目汇总
-- 用途: 按项目/分系统汇总物料数量、长周期占比、外协占比
-- 对应大屏: 物料专题大屏
-- 数据表: biz_dws_material_period_summary
-- ================================================================

SELECT
  project_no,
  subsystem,
  total_material_cnt,
  long_cycle_cnt,
  long_cycle_rate,
  self_developed_cnt,
  outsource_cnt,
  outsource_rate,
  has_risk_cnt,
  risk_rate,
  supplier_count,
  earliest_delivery,
  latest_delivery
FROM biz_dws_material_period_summary
ORDER BY project_no, subsystem

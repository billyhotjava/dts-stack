-- ================================================================
-- 查询卡片: 物料域KPI
-- 用途: 按项目汇总物料总量、长周期占比、外协占比、风险物料数
-- 对应大屏: S1(综合态势), 物料专题大屏
-- 数据表: biz_ads_material_kpi
-- ================================================================

SELECT
  project_no,
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
FROM biz_ads_material_kpi
ORDER BY project_no

{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'material']) }}

SELECT
  d.project_no,
  d.subsystem,

  COUNT(*)                                                                       AS total_cnt,

  -- 长周期
  SUM(CASE WHEN d.is_long_cycle THEN 1 ELSE 0 END)                              AS long_cycle_cnt,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN d.is_long_cycle THEN 1 ELSE 0 END)::numeric
         / COUNT(*)::numeric, 4)
  END                                                                            AS long_cycle_rate,

  -- 自研 vs 外协
  SUM(CASE WHEN d.is_self_developed THEN 1 ELSE 0 END)                          AS self_developed_cnt,
  SUM(CASE WHEN NOT d.is_self_developed THEN 1 ELSE 0 END)                      AS outsource_cnt,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN NOT d.is_self_developed THEN 1 ELSE 0 END)::numeric
         / COUNT(*)::numeric, 4)
  END                                                                            AS outsource_rate,

  -- 风险
  SUM(CASE WHEN d.risk_rank > 0 THEN 1 ELSE 0 END)                              AS has_risk_cnt,

  -- 供应商数
  COUNT(DISTINCT d.supplier_name)                                                AS supplier_count

FROM {{ ref('biz_dwd_material_info') }} d
WHERE d.project_no IS NOT NULL
GROUP BY d.project_no, d.subsystem

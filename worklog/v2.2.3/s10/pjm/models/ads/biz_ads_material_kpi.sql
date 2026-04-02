{{ config(materialized='table', tags=['project-management', 'biz', 'ads', 'kpi', 'material']) }}

SELECT
  s.project_no,

  SUM(s.total_cnt)                                                               AS total_cnt,
  SUM(s.long_cycle_cnt)                                                          AS long_cycle_cnt,
  CASE WHEN SUM(s.total_cnt) = 0 THEN 0
       ELSE ROUND(
         SUM(s.long_cycle_cnt)::numeric
         / SUM(s.total_cnt)::numeric, 4)
  END                                                                            AS long_cycle_rate,

  SUM(s.self_developed_cnt)                                                      AS self_developed_cnt,
  SUM(s.outsource_cnt)                                                           AS outsource_cnt,
  CASE WHEN SUM(s.total_cnt) = 0 THEN 0
       ELSE ROUND(
         SUM(s.outsource_cnt)::numeric
         / SUM(s.total_cnt)::numeric, 4)
  END                                                                            AS outsource_rate,

  SUM(s.has_risk_cnt)                                                            AS has_risk_cnt,
  SUM(s.supplier_count)                                                          AS supplier_count

FROM {{ ref('biz_dws_material_period_summary') }} s
GROUP BY s.project_no
ORDER BY s.project_no

{{ config(materialized='table', tags=['project-management-v3', 'biz', 'ads', 'material']) }}

SELECT
  s.period_year,
  s.period_month,
  s.dept_owner,
  SUM(s.material_cnt) AS material_cnt,
  SUM(s.delivered_cnt) AS delivered_cnt,
  SUM(s.on_time_delivery_cnt) AS on_time_delivery_cnt,
  SUM(s.overdue_undelivered_cnt) AS overdue_undelivered_cnt,
  SUM(s.long_cycle_cnt) AS long_cycle_cnt,
  SUM(s.high_risk_cnt) AS high_risk_cnt,
  SUM(s.long_cycle_high_risk_cnt) AS long_cycle_high_risk_cnt,
  SUM(s.affects_major_node_cnt) AS affects_major_node_cnt,
  CASE WHEN SUM(s.material_cnt) = 0 THEN 0
       ELSE ROUND(SUM(s.delivered_cnt)::numeric / SUM(s.material_cnt)::numeric * 100, 2)
  END AS delivery_rate,
  CASE WHEN SUM(s.delivered_cnt) = 0 THEN 0
       ELSE ROUND(SUM(s.on_time_delivery_cnt)::numeric / SUM(s.delivered_cnt)::numeric * 100, 2)
  END AS on_time_delivery_rate,
  ROUND(AVG(s.avg_delay_days), 2) AS avg_delay_days,
  MAX(s.max_delay_days) AS max_delay_days
FROM {{ ref('biz_dws_material_delivery_monthly_v2') }} s
GROUP BY s.period_year, s.period_month, s.dept_owner

{{ config(materialized='table', tags=['project-management-v3', 'biz', 'ads', 'quality-measure']) }}

SELECT
  s.period_year,
  s.period_month,
  SUM(s.measure_cnt) AS measure_cnt,
  SUM(s.closed_cnt) AS closed_cnt,
  SUM(s.open_cnt) AS open_cnt,
  SUM(s.special_measure_cnt) AS special_measure_cnt,
  CASE WHEN SUM(s.measure_cnt) = 0 THEN 0
       ELSE ROUND(SUM(s.closed_cnt)::numeric / SUM(s.measure_cnt)::numeric * 100, 2)
  END AS closure_rate,
  ROUND(AVG(s.avg_closure_days), 2) AS avg_closure_days,
  MAX(s.max_pending_days) AS max_pending_days
FROM {{ ref('biz_dws_quality_measure_monthly_v2') }} s
GROUP BY s.period_year, s.period_month

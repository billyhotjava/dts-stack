{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dws', 'tech-state-measure']) }}

SELECT
  d.follow_up_year AS period_year,
  d.follow_up_month AS period_month,
  d.project_no,
  d.dept,
  COUNT(*) AS measure_cnt,
  SUM(CASE WHEN d.is_closed THEN 1 ELSE 0 END) AS closed_cnt,
  SUM(CASE WHEN NOT d.is_closed THEN 1 ELSE 0 END) AS open_cnt,
  SUM(CASE WHEN d.measure_category = '专项跟进' THEN 1 ELSE 0 END) AS special_measure_cnt,
  AVG(d.closure_days) FILTER (WHERE d.closure_days IS NOT NULL) AS avg_closure_days,
  MAX(d.pending_days) FILTER (WHERE NOT d.is_closed) AS max_pending_days
FROM {{ ref('biz_dwd_tech_state_measure_v2') }} d
WHERE d.follow_up_month IS NOT NULL
GROUP BY d.follow_up_year, d.follow_up_month, d.project_no, d.dept

{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dws', 'material']) }}

SELECT
  d.contract_delivery_year AS period_year,
  d.contract_delivery_month AS period_month,
  d.project_no,
  d.dept_owner,
  COUNT(*) AS material_cnt,
  SUM(CASE WHEN d.is_delivered THEN 1 ELSE 0 END) AS delivered_cnt,
  SUM(CASE WHEN d.is_on_time_delivery THEN 1 ELSE 0 END) AS on_time_delivery_cnt,
  SUM(CASE WHEN d.is_overdue_undelivered THEN 1 ELSE 0 END) AS overdue_undelivered_cnt,
  SUM(CASE WHEN d.is_long_cycle THEN 1 ELSE 0 END) AS long_cycle_cnt,
  SUM(CASE WHEN d.is_high_risk THEN 1 ELSE 0 END) AS high_risk_cnt,
  SUM(CASE WHEN d.is_long_cycle AND d.is_high_risk THEN 1 ELSE 0 END) AS long_cycle_high_risk_cnt,
  SUM(CASE WHEN d.affects_major_node THEN 1 ELSE 0 END) AS affects_major_node_cnt,
  AVG(d.delivery_delay_days) FILTER (WHERE d.delivery_delay_days > 0) AS avg_delay_days,
  MAX(d.delivery_delay_days) AS max_delay_days
FROM {{ ref('biz_dwd_material_delivery_v2') }} d
WHERE d.contract_delivery_month IS NOT NULL
GROUP BY d.contract_delivery_year, d.contract_delivery_month, d.project_no, d.dept_owner

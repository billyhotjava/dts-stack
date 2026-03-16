{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'admin-data-lake']) }}

{{
  config(
    materialized='table',
    tags=['project-management', 'biz', 'dws']
  )
}}

SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,
  d.project_no,
  d.risk_level,
  d.node_type,

  COUNT(*)                                           AS total_cnt,
  SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END)  AS incomplete_cnt,
  SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)   AS completed_cnt

FROM {{ ref('biz_dwd_project_node') }} d
WHERE d.plan_year IS NOT NULL
  AND d.risk_level IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month, d.project_no, d.risk_level, d.node_type

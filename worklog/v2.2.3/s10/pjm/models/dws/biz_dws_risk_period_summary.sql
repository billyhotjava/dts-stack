{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'risk']) }}

SELECT
  d.submit_year                                                                  AS period_year,
  d.submit_month                                                                 AS period_month,
  d.project_no,
  d.dept,

  COUNT(*)                                                                       AS total_risk_cnt,

  -- 按 risk_level 分类
  SUM(CASE WHEN d.risk_level = '高' THEN 1 ELSE 0 END)                          AS high_cnt,
  SUM(CASE WHEN d.risk_level = '中' THEN 1 ELSE 0 END)                          AS mid_cnt,
  SUM(CASE WHEN d.risk_level = '低' THEN 1 ELSE 0 END)                          AS low_cnt,

  -- 释放状态
  SUM(CASE WHEN d.is_released THEN 1 ELSE 0 END)                                AS released_cnt,
  SUM(CASE WHEN NOT d.is_released THEN 1 ELSE 0 END)                            AS open_cnt,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN d.is_released THEN 1 ELSE 0 END)::numeric
         / COUNT(*)::numeric, 4)
  END                                                                            AS release_rate

FROM {{ ref('biz_dwd_risk_info') }} d
WHERE d.submit_year IS NOT NULL
GROUP BY d.submit_year, d.submit_month, d.project_no, d.dept

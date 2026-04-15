{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dws', 'risk']) }}

-- 风险域月度汇总
-- 粒度: project_no × dept × submit_month

SELECT
  d.submit_year                                                                  AS period_year,
  d.submit_month                                                                 AS period_month,
  d.project_no,
  d.dept,

  COUNT(*)                                                                       AS total_risk_cnt,

  -- 按 risk_level 分类（字典派生布尔）
  SUM(CASE WHEN d.is_high_risk THEN 1 ELSE 0 END)                               AS high_cnt,
  SUM(CASE WHEN d.is_mid_risk  THEN 1 ELSE 0 END)                               AS mid_cnt,
  SUM(CASE WHEN d.is_low_risk  THEN 1 ELSE 0 END)                               AS low_cnt,

  -- 释放状态
  SUM(CASE WHEN d.is_released THEN 1 ELSE 0 END)                                AS released_cnt,
  SUM(CASE WHEN NOT d.is_released THEN 1 ELSE 0 END)                            AS open_cnt

FROM {{ ref('biz_dwd_risk_info_v2') }} d
WHERE d.submit_year IS NOT NULL
GROUP BY d.submit_year, d.submit_month, d.project_no, d.dept

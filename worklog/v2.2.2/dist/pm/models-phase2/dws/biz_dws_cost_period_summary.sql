{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'cost']) }}

-- 成本域周期汇总：按 (year, month, project_no, dept) 聚合
-- 对应 T02 成本与预算控制指标

SELECT
  c.period_year,
  c.period_month,
  c.project_no,
  c.dept,

  -- === 金额汇总 ===
  SUM(COALESCE(c.budget_amount, 0))                    AS budget_total,
  SUM(COALESCE(c.actual_amount, 0))                    AS actual_total,
  SUM(COALESCE(c.deviation_amount, 0))                 AS deviation_total,

  -- === 预算执行率 ===
  CASE
    WHEN SUM(COALESCE(c.budget_amount, 0)) = 0 THEN NULL
    ELSE ROUND(
      SUM(COALESCE(c.actual_amount, 0))::numeric
      / SUM(COALESCE(c.budget_amount, 0))::numeric, 4)
  END AS execution_rate,

  -- === 偏差率 ===
  CASE
    WHEN SUM(COALESCE(c.budget_amount, 0)) = 0 THEN NULL
    ELSE ROUND(
      SUM(COALESCE(c.deviation_amount, 0))::numeric
      / SUM(COALESCE(c.budget_amount, 0))::numeric, 4)
  END AS deviation_rate,

  -- === 项目数 ===
  COUNT(DISTINCT c.project_no) AS project_cnt

FROM {{ ref('biz_dwd_cost_accounting') }} c
WHERE c.period_year IS NOT NULL
GROUP BY c.period_year, c.period_month, c.project_no, c.dept

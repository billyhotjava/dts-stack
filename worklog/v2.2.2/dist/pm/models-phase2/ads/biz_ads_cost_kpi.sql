{{ config(materialized='table', tags=['project-management', 'biz', 'ads', 'kpi', 'cost']) }}

-- 成本域 KPI 汇总
-- 对应 T02 成本与预算控制指标矩阵 + CostScreen mockData
-- 粒度: (period_year, period_month)

WITH monthly AS (
  SELECT
    period_year,
    period_month,

    SUM(budget_total)                                  AS budget_total,
    SUM(actual_total)                                  AS actual_total,
    SUM(deviation_total)                               AS deviation_total,

    CASE
      WHEN SUM(budget_total) = 0 THEN NULL
      ELSE ROUND(SUM(actual_total)::numeric / SUM(budget_total)::numeric, 4)
    END AS execution_rate,

    CASE
      WHEN SUM(budget_total) = 0 THEN NULL
      ELSE ROUND(SUM(deviation_total)::numeric / SUM(budget_total)::numeric, 4)
    END AS deviation_rate

  FROM {{ ref('biz_dws_cost_period_summary') }}
  GROUP BY period_year, period_month
),

yearly AS (
  SELECT
    period_year,
    SUM(budget_total) AS annual_budget_total,
    SUM(actual_total) AS annual_actual_total,
    SUM(deviation_total) AS annual_deviation_total,
    CASE
      WHEN SUM(budget_total) = 0 THEN NULL
      ELSE ROUND(SUM(actual_total)::numeric / SUM(budget_total)::numeric, 4)
    END AS annual_execution_rate,
    CASE
      WHEN SUM(budget_total) = 0 THEN NULL
      ELSE ROUND(SUM(deviation_total)::numeric / SUM(budget_total)::numeric, 4)
    END AS annual_deviation_rate
  FROM {{ ref('biz_dws_cost_period_summary') }}
  GROUP BY period_year
),

dept_ranking AS (
  SELECT
    period_year,
    dept,
    SUM(budget_total) AS dept_budget,
    SUM(actual_total) AS dept_actual,
    SUM(deviation_total) AS dept_deviation,
    CASE
      WHEN SUM(budget_total) = 0 THEN NULL
      ELSE ROUND(SUM(actual_total)::numeric / SUM(budget_total)::numeric, 4)
    END AS dept_execution_rate,
    ROW_NUMBER() OVER (
      PARTITION BY period_year
      ORDER BY ABS(SUM(deviation_total)) DESC
    ) AS deviation_rank
  FROM {{ ref('biz_dws_cost_period_summary') }}
  GROUP BY period_year, dept
)

SELECT
  m.period_year,
  m.period_month,

  -- === 月度指标 ===
  m.budget_total,
  m.actual_total,
  m.deviation_total,
  m.execution_rate,
  m.deviation_rate,

  -- === 年度累计指标 ===
  y.annual_budget_total,
  y.annual_actual_total,
  y.annual_deviation_total,
  y.annual_execution_rate,
  y.annual_deviation_rate

FROM monthly m
LEFT JOIN yearly y ON y.period_year = m.period_year
ORDER BY m.period_year, m.period_month

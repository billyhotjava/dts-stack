{{ config(materialized='table', tags=['project-management', 'biz', 'ads', 'kpi', 'risk']) }}

-- 风险域 KPI 汇总
-- 对应 T02 风险与预警中心指标矩阵 + RiskScreen mockData
-- 粒度: (period_year, period_month)

SELECT
  period_year,
  period_month,

  -- === 风险总量 ===
  SUM(total_risk_cnt)                                  AS total_risk_cnt,

  -- === 风险等级分布 ===
  SUM(high_risk_cnt)                                   AS high_risk_cnt,
  SUM(mid_risk_cnt)                                    AS mid_risk_cnt,
  SUM(low_risk_cnt)                                    AS low_risk_cnt,

  -- === 闭环统计 ===
  SUM(closed_cnt)                                      AS closed_cnt,
  SUM(open_cnt)                                        AS open_cnt,

  CASE
    WHEN SUM(total_risk_cnt) = 0 THEN 0
    ELSE ROUND(SUM(closed_cnt)::numeric / SUM(total_risk_cnt)::numeric, 4)
  END AS closure_rate,

  -- === 高风险项目数（去重） ===
  (SELECT COUNT(DISTINCT r2.project_no)
   FROM {{ ref('biz_dwd_risk_info') }} r2
   WHERE r2.risk_level = '高'
     AND NOT r2.is_closed
  ) AS high_risk_project_cnt,

  -- === 措施覆盖率 ===
  SUM(has_measure_cnt)                                 AS has_measure_cnt,
  CASE
    WHEN SUM(total_risk_cnt) = 0 THEN 0
    ELSE ROUND(SUM(has_measure_cnt)::numeric / SUM(total_risk_cnt)::numeric, 4)
  END AS measure_coverage_rate,

  -- === 平均闭环天数 ===
  ROUND(AVG(avg_pending_days)::numeric, 1) AS avg_pending_days

FROM {{ ref('biz_dws_risk_period_summary') }}
GROUP BY period_year, period_month
ORDER BY period_year, period_month

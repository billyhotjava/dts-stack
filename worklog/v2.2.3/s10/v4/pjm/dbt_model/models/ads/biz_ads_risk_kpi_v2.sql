{{ config(materialized='table', tags=['project-management-v3', 'biz', 'ads', 'kpi', 'risk']) }}

-- 风险域 KPI：月度聚合
-- 存储分子/分母，支持跨月查询

SELECT
  s.period_year,
  s.period_month,

  SUM(s.total_risk_cnt)                                                          AS total_risk_cnt,
  SUM(s.high_cnt)                                                                AS high_cnt,
  SUM(s.mid_cnt)                                                                 AS mid_cnt,
  SUM(s.low_cnt)                                                                 AS low_cnt,
  SUM(s.released_cnt)                                                            AS released_cnt,
  SUM(s.open_cnt)                                                                AS open_cnt,

  CASE WHEN SUM(s.total_risk_cnt) = 0 THEN 0
       ELSE ROUND(SUM(s.released_cnt)::numeric
                / SUM(s.total_risk_cnt)::numeric, 4)
  END AS release_rate

FROM {{ ref('biz_dws_risk_monthly_v2') }} s
GROUP BY s.period_year, s.period_month
ORDER BY s.period_year, s.period_month

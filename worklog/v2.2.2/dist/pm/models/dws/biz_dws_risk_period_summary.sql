{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'risk']) }}

-- 风险域周期汇总：按 (year, month, project_no, dept) 聚合
-- 对应 T02 风险与预警中心指标

WITH risks AS (
  SELECT * FROM {{ ref('biz_dwd_risk_info') }}
),
measures AS (
  SELECT
    project_no,
    risk_name,
    COUNT(*) AS measure_cnt
  FROM {{ ref('biz_dwd_risk_measure') }}
  GROUP BY project_no, risk_name
)

SELECT
  r.submit_year                                       AS period_year,
  r.submit_quarter                                    AS period_quarter,
  r.submit_month                                      AS period_month,
  r.project_no,
  r.dept,

  -- === 风险总量 ===
  COUNT(*)                                            AS total_risk_cnt,

  -- === 风险等级 ===
  SUM(CASE WHEN r.risk_level = '高' THEN 1 ELSE 0 END)   AS high_risk_cnt,
  SUM(CASE WHEN r.risk_level = '中' THEN 1 ELSE 0 END)   AS mid_risk_cnt,
  SUM(CASE WHEN r.risk_level = '低' THEN 1 ELSE 0 END)   AS low_risk_cnt,

  -- === 闭环统计 ===
  SUM(CASE WHEN r.is_closed THEN 1 ELSE 0 END)            AS closed_cnt,
  SUM(CASE WHEN NOT r.is_closed THEN 1 ELSE 0 END)        AS open_cnt,

  CASE
    WHEN COUNT(*) = 0 THEN 0
    ELSE ROUND(
      SUM(CASE WHEN r.is_closed THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END AS closure_rate,

  -- === 措施覆盖率 ===
  SUM(CASE WHEN m.measure_cnt > 0 THEN 1 ELSE 0 END)     AS has_measure_cnt,
  CASE
    WHEN COUNT(*) = 0 THEN 0
    ELSE ROUND(
      SUM(CASE WHEN m.measure_cnt > 0 THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END AS measure_coverage_rate,

  -- === 平均滞留天数 ===
  ROUND(AVG(r.pending_days)::numeric, 1) AS avg_pending_days

FROM risks r
LEFT JOIN measures m
  ON m.project_no = r.project_no AND m.risk_name = r.risk_name
WHERE r.submit_year IS NOT NULL
GROUP BY r.submit_year, r.submit_quarter, r.submit_month, r.project_no, r.dept

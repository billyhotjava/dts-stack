{{ config(materialized='table', tags=['project-management', 'biz', 'ads', 'kpi', 'quality']) }}

SELECT
  s.period_year,
  s.period_month,

  SUM(s.total_issue_cnt)                                                         AS total_issue_cnt,
  SUM(s.open_issue_cnt)                                                          AS open_issue_cnt,
  SUM(s.closed_cnt)                                                              AS closed_cnt,
  CASE WHEN SUM(s.total_issue_cnt) = 0 THEN 0
       ELSE ROUND(
         SUM(s.closed_cnt)::numeric
         / SUM(s.total_issue_cnt)::numeric, 4)
  END                                                                            AS closure_rate,

  SUM(s.has_zero_plan_cnt)                                                       AS has_zero_plan_cnt,
  SUM(s.zero_plan_synced_cnt)                                                    AS zero_plan_synced_cnt,

  SUM(s.cat_design)                                                              AS cat_design,
  SUM(s.cat_process)                                                             AS cat_process,
  SUM(s.cat_management)                                                          AS cat_management,
  SUM(s.cat_component)                                                           AS cat_component,
  SUM(s.cat_operation)                                                           AS cat_operation,
  SUM(s.cat_outsource)                                                           AS cat_outsource,
  SUM(s.cat_software)                                                            AS cat_software,
  SUM(s.cat_other)                                                               AS cat_other,

  SUM(s.measure_count)                                                           AS measure_count

FROM {{ ref('biz_dws_quality_period_summary') }} s
GROUP BY s.period_year, s.period_month
ORDER BY s.period_year, s.period_month

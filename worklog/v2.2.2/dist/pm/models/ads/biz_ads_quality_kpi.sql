{{ config(materialized='table', tags=['project-management', 'biz', 'ads', 'kpi', 'quality']) }}

-- 质量域 KPI 汇总
-- 对应 PDF P5 质量统计 + T02 管控层指标矩阵
-- 粒度: (period_year, period_month)

SELECT
  period_year,
  period_month,

  -- === 问题总量 ===
  SUM(total_issue_cnt)                                 AS total_issue_cnt,
  SUM(new_issue_cnt)                                   AS new_issue_cnt,

  -- === 状态汇总 ===
  SUM(open_issue_cnt)                                  AS open_issue_cnt,
  SUM(tech_zero_cnt)                                   AS tech_zero_cnt,
  SUM(mgmt_zero_cnt)                                   AS mgmt_zero_cnt,
  SUM(both_zero_cnt)                                   AS both_zero_cnt,
  SUM(zero_completed_cnt)                              AS zero_completed_cnt,

  -- === 闭环统计 ===
  SUM(closed_cnt)                                      AS closed_cnt,
  CASE
    WHEN SUM(total_issue_cnt) = 0 THEN 0
    ELSE ROUND(SUM(closed_cnt)::numeric / SUM(total_issue_cnt)::numeric, 4)
  END AS closure_rate,

  -- === 归零完成率 ===
  CASE
    WHEN SUM(total_issue_cnt) = 0 THEN 0
    ELSE ROUND(SUM(zero_completed_cnt)::numeric / SUM(total_issue_cnt)::numeric, 4)
  END AS zero_completion_rate,

  -- === 未提交归零计划数 ===
  SUM(no_zero_plan_cnt)                                AS no_zero_plan_cnt,

  -- === 问题分类统计 ===
  SUM(cat_design_cnt)                                  AS cat_design_cnt,
  SUM(cat_process_cnt)                                 AS cat_process_cnt,
  SUM(cat_management_cnt)                              AS cat_management_cnt,
  SUM(cat_component_cnt)                               AS cat_component_cnt,
  SUM(cat_operation_cnt)                               AS cat_operation_cnt,
  SUM(cat_outsource_cnt)                               AS cat_outsource_cnt,
  SUM(cat_software_cnt)                                AS cat_software_cnt,
  SUM(cat_other_cnt)                                   AS cat_other_cnt,

  -- === 措施覆盖率 ===
  SUM(has_measure_cnt)                                 AS has_measure_cnt,
  CASE
    WHEN SUM(total_issue_cnt) = 0 THEN 0
    ELSE ROUND(SUM(has_measure_cnt)::numeric / SUM(total_issue_cnt)::numeric, 4)
  END AS measure_coverage_rate

FROM {{ ref('biz_dws_quality_period_summary') }}
GROUP BY period_year, period_month
ORDER BY period_year, period_month

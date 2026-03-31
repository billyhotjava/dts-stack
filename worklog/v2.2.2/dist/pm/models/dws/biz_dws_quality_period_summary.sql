{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'quality']) }}

-- 质量域周期汇总：按 (year, month, project_no, dept) 聚合
-- 对应 PDF P5 质量统计表的全部指标

WITH issues AS (
  SELECT * FROM {{ ref('biz_dwd_quality_issue') }}
),
measures AS (
  SELECT
    project_no,
    issue_name,
    COUNT(*) AS measure_cnt
  FROM {{ ref('biz_dwd_quality_measure') }}
  GROUP BY project_no, issue_name
)

SELECT
  i.issue_year                                        AS period_year,
  i.issue_quarter                                     AS period_quarter,
  i.issue_month                                       AS period_month,
  i.project_no,
  i.dept,

  -- === 问题总量 ===
  COUNT(*)                                            AS total_issue_cnt,

  -- === 新增质量问题数（该周期内发生） ===
  COUNT(*)                                            AS new_issue_cnt,

  -- === 状态汇总 ===
  SUM(CASE WHEN NOT i.is_zero_completed THEN 1 ELSE 0 END)  AS open_issue_cnt,
  SUM(CASE WHEN i.is_tech_zero THEN 1 ELSE 0 END)           AS tech_zero_cnt,
  SUM(CASE WHEN i.is_mgmt_zero THEN 1 ELSE 0 END)           AS mgmt_zero_cnt,
  SUM(CASE WHEN i.is_both_zero THEN 1 ELSE 0 END)           AS both_zero_cnt,
  SUM(CASE WHEN i.is_zero_completed THEN 1 ELSE 0 END)      AS zero_completed_cnt,

  -- === 闭环统计 ===
  SUM(CASE WHEN i.is_closed THEN 1 ELSE 0 END)              AS closed_cnt,
  CASE
    WHEN COUNT(*) = 0 THEN 0
    ELSE ROUND(
      SUM(CASE WHEN i.is_closed THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END AS closure_rate,

  -- === 归零计划统计 ===
  SUM(CASE WHEN NOT i.has_zero_plan THEN 1 ELSE 0 END)      AS no_zero_plan_cnt,

  -- === 问题分类统计 ===
  SUM(CASE WHEN i.issue_category = '设计' THEN 1 ELSE 0 END)      AS cat_design_cnt,
  SUM(CASE WHEN i.issue_category = '工艺' THEN 1 ELSE 0 END)      AS cat_process_cnt,
  SUM(CASE WHEN i.issue_category = '管理' THEN 1 ELSE 0 END)      AS cat_management_cnt,
  SUM(CASE WHEN i.issue_category = '元器件' THEN 1 ELSE 0 END)    AS cat_component_cnt,
  SUM(CASE WHEN i.issue_category = '操作' THEN 1 ELSE 0 END)      AS cat_operation_cnt,
  SUM(CASE WHEN i.issue_category = '外协外购' THEN 1 ELSE 0 END)  AS cat_outsource_cnt,
  SUM(CASE WHEN i.issue_category = '软件' THEN 1 ELSE 0 END)      AS cat_software_cnt,
  SUM(CASE WHEN i.issue_category = '其他' THEN 1 ELSE 0 END)      AS cat_other_cnt,

  -- === 措施覆盖率 ===
  SUM(CASE WHEN m.measure_cnt > 0 THEN 1 ELSE 0 END)              AS has_measure_cnt,
  CASE
    WHEN COUNT(*) = 0 THEN 0
    ELSE ROUND(
      SUM(CASE WHEN m.measure_cnt > 0 THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END AS measure_coverage_rate

FROM issues i
LEFT JOIN measures m
  ON m.project_no = i.project_no AND m.issue_name = i.issue_name
WHERE i.issue_year IS NOT NULL
GROUP BY i.issue_year, i.issue_quarter, i.issue_month, i.project_no, i.dept

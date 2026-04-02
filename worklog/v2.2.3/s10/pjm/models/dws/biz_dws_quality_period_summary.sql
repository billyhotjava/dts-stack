{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'quality']) }}

-- 先独立聚合 issue 和 measure，再 JOIN，避免一对多膨胀
WITH issue_agg AS (
  SELECT
    issue_year                       AS period_year,
    issue_month                      AS period_month,
    project_no,
    dept,
    COUNT(*)                                                                   AS total_issue_cnt,
    SUM(CASE WHEN status = '未完成归零' THEN 1 ELSE 0 END)                      AS open_issue_cnt,
    SUM(CASE WHEN zero_complete_date IS NOT NULL THEN 1 ELSE 0 END)             AS closed_cnt,
    SUM(CASE WHEN has_zero_plan THEN 1 ELSE 0 END)                             AS has_zero_plan_cnt,
    SUM(CASE WHEN zero_plan_synced IS NOT NULL
          AND btrim(zero_plan_synced) != '' THEN 1 ELSE 0 END)                 AS zero_plan_synced_cnt,
    SUM(CASE WHEN issue_category = '设计' THEN 1 ELSE 0 END)                   AS cat_design,
    SUM(CASE WHEN issue_category = '工艺' THEN 1 ELSE 0 END)                   AS cat_process,
    SUM(CASE WHEN issue_category = '管理' THEN 1 ELSE 0 END)                   AS cat_management,
    SUM(CASE WHEN issue_category = '元器件' THEN 1 ELSE 0 END)                 AS cat_component,
    SUM(CASE WHEN issue_category = '操作' THEN 1 ELSE 0 END)                   AS cat_operation,
    SUM(CASE WHEN issue_category = '外协' THEN 1 ELSE 0 END)                   AS cat_outsource,
    SUM(CASE WHEN issue_category = '软件' THEN 1 ELSE 0 END)                   AS cat_software,
    SUM(CASE WHEN issue_category NOT IN ('设计','工艺','管理','元器件','操作','外协','软件')
              OR issue_category IS NULL THEN 1 ELSE 0 END)                      AS cat_other
  FROM {{ ref('biz_dwd_quality_issue') }}
  WHERE issue_year IS NOT NULL
  GROUP BY issue_year, issue_month, project_no, dept
),

measure_agg AS (
  SELECT
    project_no,
    COUNT(*) AS measure_count
  FROM {{ ref('biz_dwd_quality_measure') }}
  WHERE project_no IS NOT NULL
  GROUP BY project_no
)

SELECT
  i.period_year,
  i.period_month,
  i.project_no,
  i.dept,
  i.total_issue_cnt,
  i.open_issue_cnt,
  i.closed_cnt,
  CASE WHEN i.total_issue_cnt = 0 THEN 0
       ELSE ROUND(i.closed_cnt::numeric / i.total_issue_cnt::numeric, 4)
  END AS closure_rate,
  i.has_zero_plan_cnt,
  i.zero_plan_synced_cnt,
  i.cat_design,
  i.cat_process,
  i.cat_management,
  i.cat_component,
  i.cat_operation,
  i.cat_outsource,
  i.cat_software,
  i.cat_other,
  COALESCE(m.measure_count, 0) AS measure_count

FROM issue_agg i
LEFT JOIN measure_agg m ON m.project_no = i.project_no

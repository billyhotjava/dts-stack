{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'quality']) }}

SELECT
  qi.issue_year                                                                  AS period_year,
  qi.issue_month                                                                 AS period_month,
  qi.project_no,
  qi.dept,

  COUNT(DISTINCT qi.issue_id)                                                    AS total_issue_cnt,
  SUM(CASE WHEN qi.status = '未完成归零' THEN 1 ELSE 0 END)                      AS open_issue_cnt,
  SUM(CASE WHEN qi.zero_complete_date IS NOT NULL THEN 1 ELSE 0 END)             AS closed_cnt,
  CASE WHEN COUNT(DISTINCT qi.issue_id) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN qi.zero_complete_date IS NOT NULL THEN 1 ELSE 0 END)::numeric
         / COUNT(DISTINCT qi.issue_id)::numeric, 4)
  END                                                                            AS closure_rate,

  -- 归零计划
  SUM(CASE WHEN qi.has_zero_plan THEN 1 ELSE 0 END)                             AS has_zero_plan_cnt,
  SUM(CASE WHEN qi.zero_plan_synced IS NOT NULL
        AND btrim(qi.zero_plan_synced) != '' THEN 1 ELSE 0 END)                 AS zero_plan_synced_cnt,

  -- 按 issue_category 分类计数
  SUM(CASE WHEN qi.issue_category = '设计' THEN 1 ELSE 0 END)                   AS cat_design,
  SUM(CASE WHEN qi.issue_category = '工艺' THEN 1 ELSE 0 END)                   AS cat_process,
  SUM(CASE WHEN qi.issue_category = '管理' THEN 1 ELSE 0 END)                   AS cat_management,
  SUM(CASE WHEN qi.issue_category = '元器件' THEN 1 ELSE 0 END)                 AS cat_component,
  SUM(CASE WHEN qi.issue_category = '操作' THEN 1 ELSE 0 END)                   AS cat_operation,
  SUM(CASE WHEN qi.issue_category = '外协' THEN 1 ELSE 0 END)                   AS cat_outsource,
  SUM(CASE WHEN qi.issue_category = '软件' THEN 1 ELSE 0 END)                   AS cat_software,
  SUM(CASE WHEN qi.issue_category NOT IN ('设计','工艺','管理','元器件','操作','外协','软件')
            OR qi.issue_category IS NULL THEN 1 ELSE 0 END)                      AS cat_other,

  -- 关联措施数
  COUNT(DISTINCT qm.measure_id)                                                  AS measure_count

FROM {{ ref('biz_dwd_quality_issue') }} qi
LEFT JOIN {{ ref('biz_dwd_quality_measure') }} qm
  ON qi.project_no = qm.project_no
 AND qi.issue_name = qm.issue_name
WHERE qi.issue_year IS NOT NULL
GROUP BY qi.issue_year, qi.issue_month, qi.project_no, qi.dept

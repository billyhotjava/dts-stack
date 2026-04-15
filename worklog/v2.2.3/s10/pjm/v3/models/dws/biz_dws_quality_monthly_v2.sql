{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dws', 'quality']) }}

-- 质量域月度汇总：对齐原始指标大表 #34-48
-- 粒度: project_no × dept × issue_month
-- 存储分子/分母原始值，支持跨月查询

SELECT
  d.issue_year                                                                      AS period_year,
  d.issue_month                                                                     AS period_month,
  d.project_no,
  d.dept,

  -- #34 新增质量问题数（本月发生）
  COUNT(*)                                                                          AS new_issue_cnt,

  -- #35 现存质量问题数（未归零，截止状态快照）
  SUM(CASE WHEN d.status = '未完成归零' THEN 1 ELSE 0 END)                          AS open_issue_cnt,

  -- #36 已完成技术归零数
  SUM(CASE WHEN d.is_tech_zero AND NOT d.is_both_zero THEN 1 ELSE 0 END)            AS tech_zero_cnt,
  -- #37 已完成管理归零数
  SUM(CASE WHEN d.is_mgmt_zero AND NOT d.is_both_zero THEN 1 ELSE 0 END)           AS mgmt_zero_cnt,
  -- #38 已完成技术和管理归零数
  SUM(CASE WHEN d.is_both_zero THEN 1 ELSE 0 END)                                  AS both_zero_cnt,
  -- #39 已完成归零数合计
  SUM(CASE WHEN d.is_zero_completed THEN 1 ELSE 0 END)                             AS zero_completed_cnt,

  -- #40 未提交归零计划 (TBD-2)
  SUM(CASE WHEN NOT d.has_zero_plan THEN 1 ELSE 0 END)                             AS no_zero_plan_cnt,

  -- #41-48 按原因分类（限定 status='未完成归零'）(TBD-3)
  SUM(CASE WHEN d.status = '未完成归零' AND d.issue_category = '设计'
           THEN 1 ELSE 0 END)                                                      AS open_cat_design,
  SUM(CASE WHEN d.status = '未完成归零' AND d.issue_category = '工艺'
           THEN 1 ELSE 0 END)                                                      AS open_cat_process,
  SUM(CASE WHEN d.status = '未完成归零' AND d.issue_category = '管理'
           THEN 1 ELSE 0 END)                                                      AS open_cat_management,
  SUM(CASE WHEN d.status = '未完成归零' AND d.issue_category = '元器件'
           THEN 1 ELSE 0 END)                                                      AS open_cat_component,
  SUM(CASE WHEN d.status = '未完成归零' AND d.issue_category = '操作'
           THEN 1 ELSE 0 END)                                                      AS open_cat_operation,
  SUM(CASE WHEN d.status = '未完成归零' AND d.issue_category = '外协外购'
           THEN 1 ELSE 0 END)                                                      AS open_cat_outsource,
  SUM(CASE WHEN d.status = '未完成归零' AND d.issue_category = '软件'
           THEN 1 ELSE 0 END)                                                      AS open_cat_software,
  SUM(CASE WHEN d.status = '未完成归零'
           AND COALESCE(d.issue_category, '其他') NOT IN
               ('设计','工艺','管理','元器件','操作','外协外购','软件')
           THEN 1 ELSE 0 END)                                                      AS open_cat_other

FROM {{ ref('biz_dwd_quality_issue_v2') }} d
WHERE d.issue_year IS NOT NULL
GROUP BY d.issue_year, d.issue_month, d.project_no, d.dept

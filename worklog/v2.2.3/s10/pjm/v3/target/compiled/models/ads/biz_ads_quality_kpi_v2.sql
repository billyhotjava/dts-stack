

-- 质量域原始指标 ADS：对齐原始指标大表 #34-48
-- 粒度: period_month（跨项目/科室聚合）
-- 存储分子/分母，支持跨月查询

SELECT
  s.period_year,
  s.period_month,

  -- #34 新增质量问题数
  SUM(s.new_issue_cnt)                       AS new_issue_cnt,
  -- #35 现存质量问题数
  SUM(s.open_issue_cnt)                      AS open_issue_cnt,
  -- #36 已完成技术归零数
  SUM(s.tech_zero_cnt)                       AS tech_zero_cnt,
  -- #37 已完成管理归零数
  SUM(s.mgmt_zero_cnt)                       AS mgmt_zero_cnt,
  -- #38 已完成技术和管理归零数
  SUM(s.both_zero_cnt)                       AS both_zero_cnt,
  -- #39 已完成归零数合计
  SUM(s.zero_completed_cnt)                  AS zero_completed_cnt,
  -- #40 未提交归零计划
  SUM(s.no_zero_plan_cnt)                    AS no_zero_plan_cnt,

  -- #41-48 按原因分类（限定未完成归零）
  SUM(s.open_cat_design)                     AS open_cat_design,
  SUM(s.open_cat_process)                    AS open_cat_process,
  SUM(s.open_cat_management)                 AS open_cat_management,
  SUM(s.open_cat_component)                  AS open_cat_component,
  SUM(s.open_cat_operation)                  AS open_cat_operation,
  SUM(s.open_cat_outsource)                  AS open_cat_outsource,
  SUM(s.open_cat_software)                   AS open_cat_software,
  SUM(s.open_cat_other)                      AS open_cat_other

FROM "biadmin"."public"."biz_dws_quality_monthly_v2" s
GROUP BY s.period_year, s.period_month
ORDER BY s.period_year, s.period_month
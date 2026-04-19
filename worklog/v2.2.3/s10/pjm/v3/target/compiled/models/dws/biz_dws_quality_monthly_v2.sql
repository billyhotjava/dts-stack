

-- 质量域月度汇总：对齐原始指标大表 #34-48
-- 粒度: project_no × dept × issue_month
-- 所有分类基于 DWD 布尔字段（字典派生），不再硬编码中文枚举

SELECT
  d.issue_year                                                                      AS period_year,
  d.issue_month                                                                     AS period_month,
  d.project_no,
  d.dept,

  -- #34 新增质量问题数（本月发生）
  COUNT(*)                                                                          AS new_issue_cnt,

  -- #35 现存质量问题数（未归零）
  SUM(CASE WHEN NOT d.is_zero_completed THEN 1 ELSE 0 END)                          AS open_issue_cnt,

  -- #36 已完成技术归零数
  SUM(CASE WHEN d.is_tech_zero AND NOT d.is_both_zero THEN 1 ELSE 0 END)            AS tech_zero_cnt,
  -- #37 已完成管理归零数
  SUM(CASE WHEN d.is_mgmt_zero AND NOT d.is_both_zero THEN 1 ELSE 0 END)            AS mgmt_zero_cnt,
  -- #38 已完成技术和管理归零数
  SUM(CASE WHEN d.is_both_zero THEN 1 ELSE 0 END)                                   AS both_zero_cnt,
  -- #39 已完成归零数合计
  SUM(CASE WHEN d.is_zero_completed THEN 1 ELSE 0 END)                              AS zero_completed_cnt,

  -- #40 未提交归零计划
  SUM(CASE WHEN NOT d.has_zero_plan THEN 1 ELSE 0 END)                              AS no_zero_plan_cnt,

  -- #41-48 按原因分类（限定未归零）
  SUM(CASE WHEN NOT d.is_zero_completed AND d.cat_design      THEN 1 ELSE 0 END)    AS open_cat_design,
  SUM(CASE WHEN NOT d.is_zero_completed AND d.cat_process     THEN 1 ELSE 0 END)    AS open_cat_process,
  SUM(CASE WHEN NOT d.is_zero_completed AND d.cat_management  THEN 1 ELSE 0 END)    AS open_cat_management,
  SUM(CASE WHEN NOT d.is_zero_completed AND d.cat_component   THEN 1 ELSE 0 END)    AS open_cat_component,
  SUM(CASE WHEN NOT d.is_zero_completed AND d.cat_operation   THEN 1 ELSE 0 END)    AS open_cat_operation,
  SUM(CASE WHEN NOT d.is_zero_completed AND d.cat_outsource   THEN 1 ELSE 0 END)    AS open_cat_outsource,
  SUM(CASE WHEN NOT d.is_zero_completed AND d.cat_software    THEN 1 ELSE 0 END)    AS open_cat_software,
  SUM(CASE WHEN NOT d.is_zero_completed AND d.cat_environment THEN 1 ELSE 0 END)    AS open_cat_environment,
  SUM(CASE WHEN NOT d.is_zero_completed AND d.cat_other       THEN 1 ELSE 0 END)    AS open_cat_other,

  -- 全量（不限定未归零）9 分类 — 用于"质量问题占比"总量视图
  SUM(CASE WHEN d.cat_design      THEN 1 ELSE 0 END)                                AS total_cat_design,
  SUM(CASE WHEN d.cat_process     THEN 1 ELSE 0 END)                                AS total_cat_process,
  SUM(CASE WHEN d.cat_management  THEN 1 ELSE 0 END)                                AS total_cat_management,
  SUM(CASE WHEN d.cat_component   THEN 1 ELSE 0 END)                                AS total_cat_component,
  SUM(CASE WHEN d.cat_operation   THEN 1 ELSE 0 END)                                AS total_cat_operation,
  SUM(CASE WHEN d.cat_outsource   THEN 1 ELSE 0 END)                                AS total_cat_outsource,
  SUM(CASE WHEN d.cat_software    THEN 1 ELSE 0 END)                                AS total_cat_software,
  SUM(CASE WHEN d.cat_environment THEN 1 ELSE 0 END)                                AS total_cat_environment,
  SUM(CASE WHEN d.cat_other       THEN 1 ELSE 0 END)                                AS total_cat_other

FROM "biadmin"."public"."biz_dwd_quality_issue_v2" d
WHERE d.issue_month IS NOT NULL
GROUP BY d.issue_year, d.issue_month, d.project_no, d.dept
{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

-- 风险类型字典 v2
-- 5 类标准值：技术 / 进度 / 成本 / 设计 / 质量

SELECT code, label,
       cat_technical, cat_schedule, cat_cost, cat_design, cat_quality, cat_other,
       sort_order
FROM (VALUES
  ('技术', '技术风险', true,  false, false, false, false, false, 1),
  ('进度', '进度风险', false, true,  false, false, false, false, 2),
  ('成本', '成本风险', false, false, true,  false, false, false, 3),
  ('设计', '设计风险', false, false, false, true,  false, false, 4),
  ('质量', '质量风险', false, false, false, false, true,  false, 5),
  -- 兜底
  ('其他', '其他风险', false, false, false, false, false, true,  9)
) AS t(code, label,
       cat_technical, cat_schedule, cat_cost, cat_design, cat_quality, cat_other,
       sort_order)

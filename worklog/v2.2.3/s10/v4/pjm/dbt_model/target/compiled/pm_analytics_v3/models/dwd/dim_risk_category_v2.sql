

SELECT
  risk_category_id,
  code,
  label,
  cat_technical,
  cat_schedule,
  cat_cost,
  cat_design,
  cat_quality,
  cat_other,
  sort_order
FROM (
  VALUES
    ('risk_category_technical', '技术', '技术风险', true,  false, false, false, false, false, 1),
    ('risk_category_schedule', '进度', '进度风险', false, true,  false, false, false, false, 2),
    ('risk_category_cost', '成本', '成本风险', false, false, true,  false, false, false, 3),
    ('risk_category_design', '设计', '设计风险', false, false, false, true,  false, false, 4),
    ('risk_category_quality', '质量', '质量风险', false, false, false, false, true,  false, 5),
    ('risk_category_other', '其他', '其他风险', false, false, false, false, false, true,  9)
) AS t(
  risk_category_id,
  code,
  label,
  cat_technical,
  cat_schedule,
  cat_cost,
  cat_design,
  cat_quality,
  cat_other,
  sort_order
)
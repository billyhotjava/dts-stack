{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

SELECT
  quality_category_id,
  code,
  label,
  cat_design,
  cat_process,
  cat_management,
  cat_component,
  cat_operation,
  cat_outsource,
  cat_software,
  cat_environment,
  cat_other,
  sort_order
FROM (
  VALUES
    ('quality_category_design', '设计', '设计', true,  false, false, false, false, false, false, false, false, 1),
    ('quality_category_process', '工艺', '工艺', false, true,  false, false, false, false, false, false, false, 2),
    ('quality_category_management', '管理', '管理', false, false, true,  false, false, false, false, false, false, 3),
    ('quality_category_component', '元器件', '元器件', false, false, false, true,  false, false, false, false, false, 4),
    ('quality_category_operation', '操作', '操作', false, false, false, false, true,  false, false, false, false, 5),
    ('quality_category_outsource', '外协', '外协', false, false, false, false, false, true,  false, false, false, 6),
    ('quality_category_software', '软件', '软件', false, false, false, false, false, false, true,  false, false, 7),
    ('quality_category_environment', '环境', '环境', false, false, false, false, false, false, false, true,  false, 8),
    ('quality_category_other', '其他', '其他', false, false, false, false, false, false, false, false, true,  9)
) AS t(
  quality_category_id,
  code,
  label,
  cat_design,
  cat_process,
  cat_management,
  cat_component,
  cat_operation,
  cat_outsource,
  cat_software,
  cat_environment,
  cat_other,
  sort_order
)

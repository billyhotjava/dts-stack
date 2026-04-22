{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

-- 质量问题原因分类字典 v2
-- 9 类标准值：设计 / 工艺 / 管理 / 元器件 / 操作 / 外协 / 软件 / 其他 / 环境
-- 含真实数据别名（如 外协外购 → 外协）

SELECT code, label,
       cat_design, cat_process, cat_management, cat_component,
       cat_operation, cat_outsource, cat_software, cat_environment, cat_other,
       sort_order
FROM (VALUES
  -- 标准
  ('设计',     '设计',     true,  false, false, false, false, false, false, false, false,  1),
  ('工艺',     '工艺',     false, true,  false, false, false, false, false, false, false,  2),
  ('管理',     '管理',     false, false, true,  false, false, false, false, false, false,  3),
  ('元器件',   '元器件',   false, false, false, true,  false, false, false, false, false,  4),
  ('操作',     '操作',     false, false, false, false, true,  false, false, false, false,  5),
  ('外协',     '外协',     false, false, false, false, false, true,  false, false, false,  6),
  ('软件',     '软件',     false, false, false, false, false, false, true,  false, false,  7),
  ('环境',     '环境',     false, false, false, false, false, false, false, true,  false,  8),
  ('其他',     '其他',     false, false, false, false, false, false, false, false, true,   9),
  -- 别名
  ('外协外购', '外协外购', false, false, false, false, false, true,  false, false, false, 11)
) AS t(code, label,
       cat_design, cat_process, cat_management, cat_component,
       cat_operation, cat_outsource, cat_software, cat_environment, cat_other,
       sort_order)

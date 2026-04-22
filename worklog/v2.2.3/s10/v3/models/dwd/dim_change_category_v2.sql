{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

-- 技术状态更改类别字典 v2
-- 标准化 code = 'I' | 'II' | 'III'（在 biz_dwd_tech_state_v2 的 CASE 做规范化）
SELECT code, label, is_cat_i, is_cat_ii, is_cat_iii, severity_rank
FROM (VALUES
  ('I',   'Ⅰ类更改', true,  false, false, 3),
  ('II',  'Ⅱ类更改', false, true,  false, 2),
  ('III', 'Ⅲ类更改', false, false, true,  1)
) AS t(code, label, is_cat_i, is_cat_ii, is_cat_iii, severity_rank)

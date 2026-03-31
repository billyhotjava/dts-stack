{{ config(materialized='table', tags=['project-management', 'dim', 'dwd']) }}

SELECT code, label, is_zero_completed, is_tech_zero, is_mgmt_zero, is_both_zero, sort_order
FROM (VALUES
  ('未完成归零',             '未完成归零',             false, false, false, false, 1),
  ('已完成技术归零',         '已完成技术归零',         true,  true,  false, false, 2),
  ('已完成管理归零',         '已完成管理归零',         true,  false, true,  false, 3),
  ('已完成技术和管理归零',   '已完成技术和管理归零',   true,  true,  true,  true,  4)
) AS t(code, label, is_zero_completed, is_tech_zero, is_mgmt_zero, is_both_zero, sort_order)

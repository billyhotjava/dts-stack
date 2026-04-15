{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

-- 质量问题状态字典 v2
-- 列：
--   code              — 源系统原始值（含真实数据中的简写/别名）
--   label             — 展示用名称
--   is_zero_completed — 是否已归零（技术或管理或两者）
--   is_tech_zero      — 技术归零完成
--   is_mgmt_zero      — 管理归零完成
--   is_both_zero      — 技术和管理双归零
--   sort_order        — 展示排序

SELECT code, label, is_zero_completed, is_tech_zero, is_mgmt_zero, is_both_zero, sort_order
FROM (VALUES
  -- 官方标准枚举
  ('未完成归零',             '未完成归零',             false, false, false, false, 1),
  ('已完成技术归零',         '已完成技术归零',         true,  true,  false, false, 2),
  ('已完成管理归零',         '已完成管理归零',         true,  false, true,  false, 3),
  ('已完成技术和管理归零',   '已完成技术和管理归零',   true,  true,  true,  true,  4),
  -- 真实数据/测试数据中的别名
  ('处理中',                 '处理中（未闭环）',       false, false, false, false, 10),
  ('进行中',                 '进行中（未闭环）',       false, false, false, false, 11),
  ('未完成',                 '未完成',                 false, false, false, false, 12),
  ('未闭环',                 '未闭环',                 false, false, false, false, 13),
  ('已归零',                 '已归零',                 true,  true,  true,  true,  20),
  ('已闭环',                 '已闭环',                 true,  true,  true,  true,  21),
  ('已完成',                 '已完成',                 true,  true,  true,  true,  22)
) AS t(code, label, is_zero_completed, is_tech_zero, is_mgmt_zero, is_both_zero, sort_order)

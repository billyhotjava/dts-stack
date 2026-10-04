{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

-- 文件签署状态字典 v2
-- applies_to_category: 适用更改类别（'I_II' / 'III' / 'ALL'）
-- is_reviewed: 是否已评估评审
-- is_signed:   是否已签署

SELECT code, label, applies_to_category, is_reviewed, is_signed, sort_order
FROM (VALUES
  -- 官方标准枚举
  ('已提出需求，未评估评审', '已提出需求，未评估评审', 'I_II',  false, false, 1),
  ('已评估评审，未签署',     '已评估评审，未签署',     'I_II',  true,  false, 2),
  ('已评估评审，已签署',     '已评估评审，已签署',     'I_II',  true,  true,  3),
  ('已提出需求，未签署',     '已提出需求，未签署',     'III',   false, false, 4),
  ('已提出需求，已签署',     '已提出需求，已签署',     'III',   false, true,  5),
  -- 真实数据别名
  ('已签署',                 '已签署',                 'ALL',   true,  true,  10),
  ('未签署',                 '未签署',                 'ALL',   false, false, 11),
  ('已提出需求并签署',       '已提出需求并签署',       'ALL',   false, true,  12),
  ('已提出需求，待签署',     '已提出需求，待签署',     'ALL',   false, false, 13),
  ('已评估评审，待签署',     '已评估评审，待签署',     'I_II',  true,  false, 14),
  ('已评估评审，已通过',     '已评估评审，已通过',     'I_II',  true,  false, 15),
  ('评审通过',               '评审通过',               'ALL',   true,  false, 16),
  ('待评审',                 '待评审',                 'ALL',   false, false, 17),
  ('待提出',                 '待提出',                 'ALL',   false, false, 18)
) AS t(code, label, applies_to_category, is_reviewed, is_signed, sort_order)

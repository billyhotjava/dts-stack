{{ config(materialized='table', tags=['project-management', 'dim', 'dwd']) }}

-- 文件签署状态维度表
-- 基于 PDF P4 技术状态统计中的签署状态枚举
SELECT code, label, is_submitted, is_reviewed, is_signed, sort_order
FROM (VALUES
  ('已提出需求，未评估评审',     '已提出需求，未评估评审',     true,  false, false, 1),
  ('已评估评审，未签署',         '已评估评审，未签署',         true,  true,  false, 2),
  ('已评估评审，已签署',         '已评估评审，已签署',         true,  true,  true,  3),
  ('已提出需求，未签署',         '已提出需求，未签署',         true,  false, false, 4),
  ('已提出需求，已签署',         '已提出需求，已签署',         true,  false, true,  5)
) AS t(code, label, is_submitted, is_reviewed, is_signed, sort_order)

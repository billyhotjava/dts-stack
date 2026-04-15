{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

SELECT code, label, applies_to_category, is_signed, sort_order
FROM (VALUES
  ('已提出需求，未评估评审', '已提出需求，未评估评审', 'I_II',  false, 1),
  ('已评估评审，未签署',     '已评估评审，未签署',     'I_II',  false, 2),
  ('已评估评审，已签署',     '已评估评审，已签署',     'I_II',  true,  3),
  ('已提出需求，未签署',     '已提出需求，未签署',     'III',   false, 4),
  ('已提出需求，已签署',     '已提出需求，已签署',     'III',   true,  5)
) AS t(code, label, applies_to_category, is_signed, sort_order)

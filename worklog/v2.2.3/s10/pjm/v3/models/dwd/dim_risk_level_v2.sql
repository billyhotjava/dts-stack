{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

-- 风险等级字典 v2
SELECT code, label, is_high, is_mid, is_low, severity_rank
FROM (VALUES
  ('高', '高风险', true,  false, false, 3),
  ('中', '中风险', false, true,  false, 2),
  ('低', '低风险', false, false, true,  1),
  -- 别名
  ('高风险', '高风险', true,  false, false, 3),
  ('中风险', '中风险', false, true,  false, 2),
  ('低风险', '低风险', false, false, true,  1)
) AS t(code, label, is_high, is_mid, is_low, severity_rank)

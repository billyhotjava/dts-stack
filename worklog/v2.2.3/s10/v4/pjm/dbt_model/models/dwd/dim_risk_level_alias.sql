{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd', 'alias']) }}

SELECT alias_raw, canonical_code
FROM (VALUES
  ('高',     '高'),
  ('高风险', '高'),
  ('中',     '中'),
  ('中风险', '中'),
  ('低',     '低'),
  ('低风险', '低')
) AS t(alias_raw, canonical_code)

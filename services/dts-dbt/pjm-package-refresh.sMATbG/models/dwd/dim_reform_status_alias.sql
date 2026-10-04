{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd', 'alias']) }}

SELECT alias_raw, canonical_code
FROM (VALUES
  ('已落实整改', '已落实整改'),
  ('已完成',     '已落实整改'),
  ('完成',       '已落实整改'),
  ('不涉及',     '不涉及'),
  ('无',         '不涉及')
) AS t(alias_raw, canonical_code)

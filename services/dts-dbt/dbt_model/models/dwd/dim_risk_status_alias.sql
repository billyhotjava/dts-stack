{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd', 'alias']) }}

SELECT alias_raw, canonical_code
FROM (VALUES
  ('已释放', '已释放')
) AS t(alias_raw, canonical_code)

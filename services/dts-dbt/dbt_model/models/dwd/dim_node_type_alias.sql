{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd', 'alias']) }}

SELECT alias_raw, canonical_code
FROM (VALUES
  ('一般',        '一般节点'),
  ('一般节点',     '一般节点'),
  ('重要',        '重要节点'),
  ('重要节点',     '重要节点'),
  ('重大',        '重大节点'),
  ('重大节点',     '重大节点'),
  ('里程碑',       '里程碑节点'),
  ('里程碑节点',   '里程碑节点')
) AS t(alias_raw, canonical_code)

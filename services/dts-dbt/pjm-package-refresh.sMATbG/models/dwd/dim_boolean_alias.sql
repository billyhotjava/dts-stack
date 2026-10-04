{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd', 'alias']) }}

SELECT alias_raw, canonical_code
FROM (VALUES
  ('Y',       '是'),
  ('YES',     '是'),
  ('TRUE',    '是'),
  ('是',      '是'),
  ('已提交',   '是'),
  ('已同步',   '是'),
  ('已完成',   '是'),
  ('已签署',   '是'),
  ('N',       '否'),
  ('NO',      '否'),
  ('FALSE',   '否'),
  ('否',      '否'),
  ('未提交',   '否'),
  ('未同步',   '否'),
  ('未完成',   '否'),
  ('未签署',   '否')
) AS t(alias_raw, canonical_code)

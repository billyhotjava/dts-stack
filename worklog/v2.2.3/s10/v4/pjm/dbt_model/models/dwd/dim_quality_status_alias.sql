{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd', 'alias']) }}

SELECT alias_raw, canonical_code
FROM (VALUES
  ('处理中',                 '未完成归零'),
  ('进行中',                 '未完成归零'),
  ('未完成',                 '未完成归零'),
  ('未闭环',                 '未完成归零'),
  ('待归零',                 '未完成归零'),
  ('未完成归零',             '未完成归零'),
  ('已完成技术归零',         '已完成技术归零'),
  ('已完成管理归零',         '已完成管理归零'),
  ('已归零',                 '已完成技术和管理归零'),
  ('已闭环',                 '已完成技术和管理归零'),
  ('已完成',                 '已完成技术和管理归零'),
  ('已完成技术和管理归零',    '已完成技术和管理归零')
) AS t(alias_raw, canonical_code)

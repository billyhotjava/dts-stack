{{ config(materialized='table', tags=['project-management', 'dim', 'dwd']) }}

SELECT code, label, is_completed, is_on_time, is_overdue_completed, is_incomplete, sort_order
FROM (VALUES
  ('正常待完成',         '正常待完成',       false, false, false, false, 1),
  ('按时完成',           '按时完成',         true,  true,  false, false, 2),
  ('超期已完成已变更',   '超期已完成已变更', true,  false, true,  false, 3),
  ('超期已完成未变更',   '超期已完成未变更', true,  false, true,  false, 4),
  ('不正常待变更',       '不正常待变更',     false, false, false, true,  5),
  ('超期未完成未变更',   '超期未完成未变更', false, false, false, true,  6),
  ('超期未完成已变更',   '超期未完成已变更', false, false, false, true,  7)
) AS t(code, label, is_completed, is_on_time, is_overdue_completed, is_incomplete, sort_order)

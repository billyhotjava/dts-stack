{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

-- 节点完成情况字典 v2
-- is_pending_normal:        正常待完成（未到期且无超期）
-- is_abnormal_pending:      不正常待变更
-- is_overdue_unchanged:     超期未完成未变更（仅超期未完成分支）
-- is_overdue_changed:       超期未完成已变更
-- is_overdue_done_unchanged: 超期已完成未变更
-- is_overdue_done_changed:   超期已完成已变更

SELECT code, label, is_completed, is_on_time, is_overdue_completed, is_incomplete,
       is_pending_normal, is_abnormal_pending, is_overdue_unchanged, is_overdue_changed,
       is_overdue_done_unchanged, is_overdue_done_changed, sort_order
FROM (VALUES
  ('正常待完成',         '正常待完成',       false, false, false, false, true,  false, false, false, false, false, 1),
  ('按时完成',           '按时完成',         true,  true,  false, false, false, false, false, false, false, false, 2),
  ('超期已完成已变更',   '超期已完成已变更', true,  false, true,  false, false, false, false, false, false, true,  3),
  ('超期已完成未变更',   '超期已完成未变更', true,  false, true,  false, false, false, false, false, true,  false, 4),
  ('不正常待变更',       '不正常待变更',     false, false, false, true,  false, true,  false, false, false, false, 5),
  ('超期未完成未变更',   '超期未完成未变更', false, false, false, true,  false, false, true,  false, false, false, 6),
  ('超期未完成已变更',   '超期未完成已变更', false, false, false, true,  false, false, false, true,  false, false, 7)
) AS t(code, label, is_completed, is_on_time, is_overdue_completed, is_incomplete,
       is_pending_normal, is_abnormal_pending, is_overdue_unchanged, is_overdue_changed,
       is_overdue_done_unchanged, is_overdue_done_changed, sort_order)

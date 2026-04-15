{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

-- 节点完成情况字典 v2
-- 业务 4 大分类（gpmc-overview-v3 对齐）：
--   is_on_time                      → 按时完成
--   is_pending_normal               → 正常待完成
--   is_overdue_completed_effective  → 超期完成（超期已完成已变更 + 超期已完成未变更）
--   is_overdue_incomplete_effective → 超期未完成（超期未完成未变更 + 超期未完成已变更 + 不正常待变更）

SELECT code, label, is_completed, is_on_time, is_overdue_completed, is_incomplete,
       is_pending_normal, is_abnormal_pending, is_overdue_unchanged, is_overdue_changed,
       is_overdue_done_unchanged, is_overdue_done_changed,
       is_overdue_completed_effective, is_overdue_incomplete_effective,
       sort_order
FROM (VALUES
  ('正常待完成',         '正常待完成',       false, false, false, false, true,  false, false, false, false, false, false, false, 1),
  ('按时完成',           '按时完成',         true,  true,  false, false, false, false, false, false, false, false, false, false, 2),
  ('超期已完成已变更',   '超期已完成已变更', true,  false, true,  false, false, false, false, false, false, true,  true,  false, 3),
  ('超期已完成未变更',   '超期已完成未变更', true,  false, true,  false, false, false, false, false, true,  false, true,  false, 4),
  ('不正常待变更',       '不正常待变更',     false, false, false, true,  false, true,  false, false, false, false, false, true,  5),
  ('超期未完成未变更',   '超期未完成未变更', false, false, false, true,  false, false, true,  false, false, false, false, true,  6),
  ('超期未完成已变更',   '超期未完成已变更', false, false, false, true,  false, false, false, true,  false, false, false, true,  7)
) AS t(code, label, is_completed, is_on_time, is_overdue_completed, is_incomplete,
       is_pending_normal, is_abnormal_pending, is_overdue_unchanged, is_overdue_changed,
       is_overdue_done_unchanged, is_overdue_done_changed,
       is_overdue_completed_effective, is_overdue_incomplete_effective,
       sort_order)

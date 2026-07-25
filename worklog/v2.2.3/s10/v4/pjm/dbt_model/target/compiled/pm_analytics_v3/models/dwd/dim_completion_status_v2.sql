

SELECT
  completion_status_id,
  code,
  label,
  is_completed,
  is_on_time,
  is_overdue_completed,
  is_incomplete,
  is_pending_normal,
  is_abnormal_pending,
  is_overdue_unchanged,
  is_overdue_changed,
  is_overdue_done_unchanged,
  is_overdue_done_changed,
  is_overdue_completed_effective,
  is_overdue_incomplete_effective,
  sort_order
FROM (
  VALUES
    ('progress_status_normal_pending', '正常待完成', '正常待完成', false, false, false, false, true,  false, false, false, false, false, false, false, 1),
    ('progress_status_on_time_done', '按时完成', '按时完成', true,  true,  false, false, false, false, false, false, false, false, false, false, 2),
    ('progress_status_overdue_done_changed', '超期已完成已变更', '超期已完成已变更', true, false, true, false, false, false, false, false, false, true,  true,  false, 3),
    ('progress_status_overdue_done_unchanged', '超期已完成未变更', '超期已完成未变更', true, false, true, false, false, false, false, false, true,  false, true,  false, 4),
    ('progress_status_abnormal_pending_change', '不正常待变更', '不正常待变更', false, false, false, true, false, true,  false, false, false, false, false, true,  5),
    ('progress_status_overdue_pending_unchanged', '超期未完成未变更', '超期未完成未变更', false, false, false, true, false, false, true,  false, false, false, false, true,  6),
    ('progress_status_overdue_pending_changed', '超期未完成已变更', '超期未完成已变更', false, false, false, true, false, false, false, true,  false, false, false, true,  7)
) AS t(
  completion_status_id,
  code,
  label,
  is_completed,
  is_on_time,
  is_overdue_completed,
  is_incomplete,
  is_pending_normal,
  is_abnormal_pending,
  is_overdue_unchanged,
  is_overdue_changed,
  is_overdue_done_unchanged,
  is_overdue_done_changed,
  is_overdue_completed_effective,
  is_overdue_incomplete_effective,
  sort_order
)
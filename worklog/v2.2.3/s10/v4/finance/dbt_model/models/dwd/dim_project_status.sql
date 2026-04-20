{{ config(materialized='table', tags=['finance', 'dim', 'dwd']) }}

SELECT
  project_status_id,
  code,
  label,
  is_active,
  is_completed,
  is_pending_collection,
  is_audited,
  is_pending_expense,
  sort_order
FROM (
  VALUES
    ('project_status_in_progress',        '在研',         '在研',         true,  false, false, false, false, 1),
    ('project_status_pending_expense',    '支出待处理',   '支出待处理',   true,  false, false, false, true,  2),
    ('project_status_pending_collection', '已完成待收款', '已完成待收款', false, true,  true,  false, false, 3),
    ('project_status_audited',            '已完成审计',   '已完成审计',   false, true,  false, true,  false, 4)
) AS t(
  project_status_id,
  code,
  label,
  is_active,
  is_completed,
  is_pending_collection,
  is_audited,
  is_pending_expense,
  sort_order
)

{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

-- 节点类型字典 v2
-- is_general:      一般节点（排除口径）
-- is_important:    重要节点
-- is_major:        重大节点
-- is_milestone:    里程碑节点

SELECT code, label, is_general, is_important, is_major, is_milestone, severity_rank
FROM (VALUES
  ('一般节点',   '一般节点',   true,  false, false, false, 1),
  ('重要节点',   '重要节点',   false, true,  false, false, 2),
  ('重大节点',   '重大节点',   false, false, true,  false, 3),
  ('里程碑节点', '里程碑节点', false, false, false, true,  4)
) AS t(code, label, is_general, is_important, is_major, is_milestone, severity_rank)

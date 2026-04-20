{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd']) }}

SELECT node_type_id, code, label, is_general, is_important, is_major, is_milestone, severity_rank
FROM (
  VALUES
    ('node_type_general', '一般节点', '一般节点', true,  false, false, false, 1),
    ('node_type_important', '重要节点', '重要节点', false, true,  false, false, 2),
    ('node_type_major', '重大节点', '重大节点', false, false, true,  false, 3),
    ('node_type_milestone', '里程碑节点', '里程碑节点', false, false, false, true,  4)
) AS t(node_type_id, code, label, is_general, is_important, is_major, is_milestone, severity_rank)

-- DIM：节点类型维度
DROP TABLE IF EXISTS public.dim_node_type CASCADE;
CREATE TABLE public.dim_node_type AS
SELECT code, label, is_general, severity_rank
FROM (VALUES
  ('一般节点',   '一般节点',   true,  1),
  ('重要节点',   '重要节点',   false, 2),
  ('重大节点',   '重大节点',   false, 3),
  ('里程碑节点', '里程碑节点', false, 4)
) AS t(code, label, is_general, severity_rank);

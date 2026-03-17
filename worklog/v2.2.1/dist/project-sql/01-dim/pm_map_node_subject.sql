-- DIM：节点-主题映射（从 seed 表构建）
DROP TABLE IF EXISTS public.pm_map_node_subject CASCADE;
CREATE TABLE public.pm_map_node_subject AS
SELECT
  NULLIF(btrim(map_id), '') AS map_id,
  NULLIF(btrim(project_no), '') AS project_no,
  NULLIF(btrim(subsystem), '') AS subsystem,
  NULLIF(btrim(node_task), '') AS node_task,
  NULLIF(btrim(subproject_id), '') AS subproject_id,
  NULLIF(btrim(major_project_id), '') AS major_project_id,
  NULLIF(btrim(node_category), '') AS node_category,
  NULLIF(btrim(delay_reason_category), '') AS delay_reason_category,
  CASE
    WHEN is_key_node IS TRUE THEN true
    WHEN lower(COALESCE(is_key_node::text, '')) IN ('true', 't', '1', 'yes', 'y') THEN true
    ELSE false
  END AS is_key_node,
  CASE
    WHEN is_milestone IS TRUE THEN true
    WHEN lower(COALESCE(is_milestone::text, '')) IN ('true', 't', '1', 'yes', 'y') THEN true
    ELSE false
  END AS is_milestone,
  CASE WHEN sort_order IS NULL THEN NULL ELSE sort_order::int END AS sort_order,
  NULLIF(btrim(source_flag), '') AS source_flag,
  CASE
    WHEN remark IS NULL THEN NULL
    ELSE NULLIF(btrim(remark::text), '')
  END AS remark
FROM public.pm_map_node_subject_seed;

{{ config(materialized='table', tags=['project-management', 'dim', 'project-cockpit', 'dwd']) }}

-- 从 ODS 节点数据自动推导节点映射，无需 seed
SELECT DISTINCT
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.subsystem), '') || '|' ||
    COALESCE(btrim(o.node_task), '')
  ) AS map_id,
  {{ nullif_placeholder("o.project_no") }} AS project_no,
  {{ nullif_placeholder("o.subsystem") }} AS subsystem,
  {{ nullif_placeholder("o.node_task") }} AS node_task,
  md5(COALESCE(btrim(o.project_no), '') || '/' || COALESCE(btrim(o.subsystem), '')) AS subproject_id,
  {{ nullif_placeholder("o.project_no") }} AS major_project_id,
  CASE btrim(o.node_type)
    WHEN '里程碑节点' THEN 'milestone'
    WHEN '重大节点' THEN 'critical'
    WHEN '重要节点' THEN 'critical'
    ELSE 'routine'
  END AS node_category,
  NULL::text AS delay_reason_category,
  btrim(o.node_type) IN ('重大节点', '重要节点', '里程碑节点') AS is_key_node,
  btrim(o.node_type) = '里程碑节点' AS is_milestone,
  NULL::int AS sort_order,
  'auto' AS source_flag,
  NULL::text AS remark
FROM {{ source('pm_ods', 'project_subject_domain') }} o
WHERE btrim(COALESCE(o.project_no, '')) != ''
  AND btrim(COALESCE(o.node_task, '')) != ''

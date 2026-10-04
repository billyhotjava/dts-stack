{{ config(materialized='table', tags=['project-management', 'dim', 'project-cockpit', 'dwd']) }}

-- 从 ODS 节点数据自动推导节点映射，无需 seed
SELECT DISTINCT
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.subsystem), '') || '|' ||
    COALESCE(btrim(o.node_task), '')
  ) AS map_id,
  NULLIF(btrim(o.project_no), '') AS project_no,
  NULLIF(btrim(o.subsystem), '') AS subsystem,
  NULLIF(btrim(o.node_task), '') AS node_task,
  md5(COALESCE(btrim(o.project_no), '') || '/' || COALESCE(btrim(o.subsystem), '')) AS subproject_id,
  NULLIF(btrim(o.project_no), '') AS major_project_id,
  'normal' AS node_category,
  '' AS delay_reason_category,
  false AS is_key_node,
  false AS is_milestone,
  NULL::int AS sort_order,
  'auto' AS source_flag,
  '' AS remark
FROM {{ source('pm_ods', 'project_subject_domain') }} o
WHERE btrim(COALESCE(o.project_no, '')) != ''
  AND btrim(COALESCE(o.node_task, '')) != ''

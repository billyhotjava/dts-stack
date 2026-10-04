{{ config(materialized='table', tags=['project-management', 'dim', 'project-cockpit', 'dwd']) }}

-- 从 ODS 节点数据自动推导子项目维度，无需 seed
SELECT DISTINCT
  md5(COALESCE(btrim(o.project_no), '') || '/' || COALESCE(btrim(o.subsystem), '')) AS subproject_id,
  btrim(o.project_no) || '-' || left(
    CASE WHEN o.subsystem LIKE '%/%' THEN btrim(split_part(o.subsystem, '/', 2)) ELSE btrim(o.subsystem) END,
    8
  ) AS subproject_code,
  CASE
    WHEN o.subsystem LIKE '%/%'
      THEN NULLIF(btrim(split_part(o.subsystem, '/', 2)), '')
    ELSE NULLIF(btrim(o.subsystem), '')
  END AS subproject_name,
  NULLIF(btrim(o.project_no), '') AS major_project_id,
  NULLIF(btrim(o.project_no), '') AS project_no,
  NULLIF(btrim(o.subsystem), '') AS subsystem_name,
  NULLIF(btrim(o.dept), '') AS owner_dept,
  '' AS owner_user,
  NULLIF(btrim(o.project_manager), '') AS project_manager,
  min({{ parse_date_safe("o.plan_date") }}) AS plan_start_date,
  max({{ parse_date_safe("o.plan_date") }}) AS plan_end_date,
  NULL::date AS actual_end_date,
  '执行中' AS status,
  'A' AS priority_level,
  '' AS remark
FROM {{ source('pm_ods', 'project_subject_domain') }} o
WHERE btrim(COALESCE(o.project_no, '')) != ''
  AND btrim(COALESCE(o.subsystem, '')) != ''
GROUP BY
  COALESCE(btrim(o.project_no), ''),
  COALESCE(btrim(o.subsystem), ''),
  NULLIF(btrim(o.dept), ''),
  NULLIF(btrim(o.project_manager), '')

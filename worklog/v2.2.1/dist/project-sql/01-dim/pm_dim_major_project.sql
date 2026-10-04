{{ config(materialized='table', tags=['project-management', 'dim', 'project-cockpit', 'dwd']) }}

-- 从 ODS 节点数据自动推导重大项目维度，无需 seed
SELECT DISTINCT
  NULLIF(btrim(o.project_no), '') AS major_project_id,
  NULLIF(btrim(o.project_no), '') AS major_project_code,
  CASE
    WHEN o.subsystem LIKE '%/%'
      THEN NULLIF(btrim(split_part(o.subsystem, '/', 1)), '')
    ELSE NULLIF(btrim(o.subsystem), '')
  END AS major_project_name,
  'program-' || NULLIF(btrim(o.project_no), '') AS program_id,
  CASE
    WHEN o.subsystem LIKE '%/%'
      THEN NULLIF(btrim(split_part(o.subsystem, '/', 1)), '')
    ELSE NULLIF(btrim(o.subsystem), '')
  END AS program_name,
  '重大项目' AS project_level,
  NULLIF(btrim(o.dept), '') AS owner_dept,
  NULLIF(btrim(o.dept_leader), '') AS owner_leader,
  'A' AS priority_level,
  min({{ parse_date_safe("o.plan_date") }}) AS start_date,
  max({{ parse_date_safe("o.plan_date") }}) AS plan_end_date,
  '执行中' AS status,
  '' AS remark
FROM {{ source('pm_ods', 'project_subject_domain') }} o
WHERE btrim(COALESCE(o.project_no, '')) != ''
GROUP BY
  NULLIF(btrim(o.project_no), ''),
  CASE WHEN o.subsystem LIKE '%/%' THEN NULLIF(btrim(split_part(o.subsystem, '/', 1)), '') ELSE NULLIF(btrim(o.subsystem), '') END,
  NULLIF(btrim(o.dept), ''),
  NULLIF(btrim(o.dept_leader), '')

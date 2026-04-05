{{ config(materialized='table', tags=['project-management', 'dim', 'project-cockpit', 'dwd']) }}

-- 从 ODS 节点数据自动推导子项目维度，无需 seed
-- subsystem 直接作为子项目名称，不再用 "/" 分割
WITH raw AS (
  SELECT
    {{ nullif_placeholder("o.project_no") }} AS project_no,
    {{ nullif_placeholder("o.subsystem") }} AS subsystem,
    {{ nullif_placeholder("o.subsystem") }} AS subproject_name,
    {{ nullif_placeholder("o.dept") }} AS dept,
    {{ nullif_placeholder("o.project_manager") }} AS project_manager,
    {{ parse_date_safe("o.plan_date") }} AS plan_date
  FROM {{ source('pm_ods', 'project_subject_domain') }} o
  WHERE btrim(COALESCE(o.project_no, '')) != ''
    AND btrim(COALESCE(o.subsystem, '')) != ''
),
agg AS (
  SELECT
    project_no,
    subsystem,
    min(subproject_name) AS subproject_name,
    min(plan_date) AS plan_start_date,
    max(plan_date) AS plan_end_date
  FROM raw
  GROUP BY project_no, subsystem
),
first_dept AS (
  SELECT DISTINCT ON (project_no, subsystem)
    project_no,
    subsystem,
    dept,
    project_manager
  FROM raw
  WHERE dept IS NOT NULL
  ORDER BY project_no, subsystem, dept
)
SELECT
  md5(COALESCE(a.project_no, '') || '/' || COALESCE(a.subsystem, '')) AS subproject_id,
  a.project_no || '-' || left(COALESCE(a.subproject_name, ''), 8) AS subproject_code,
  a.subproject_name,
  a.project_no AS major_project_id,
  a.project_no,
  a.subsystem AS subsystem_name,
  d.dept AS owner_dept,
  NULL::text AS owner_user,
  d.project_manager,
  a.plan_start_date,
  a.plan_end_date,
  NULL::date AS actual_end_date,
  '执行中' AS status,
  'A' AS priority_level,
  NULL::text AS remark
FROM agg a
LEFT JOIN first_dept d ON d.project_no = a.project_no AND d.subsystem = a.subsystem

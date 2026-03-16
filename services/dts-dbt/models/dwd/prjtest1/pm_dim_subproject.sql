{{ config(materialized='table', tags=['project-management', 'dim', 'project-cockpit', 'dwd']) }}

SELECT
  NULLIF(btrim(subproject_id), '') AS subproject_id,
  NULLIF(btrim(subproject_code), '') AS subproject_code,
  NULLIF(btrim(subproject_name), '') AS subproject_name,
  NULLIF(btrim(major_project_id), '') AS major_project_id,
  NULLIF(btrim(project_no), '') AS project_no,
  NULLIF(btrim(subsystem_name), '') AS subsystem_name,
  NULLIF(btrim(owner_dept), '') AS owner_dept,
  NULLIF(btrim(owner_user), '') AS owner_user,
  NULLIF(btrim(project_manager), '') AS project_manager,
  CASE WHEN plan_start_date IS NULL THEN NULL ELSE plan_start_date::date END AS plan_start_date,
  CASE WHEN plan_end_date IS NULL THEN NULL ELSE plan_end_date::date END AS plan_end_date,
  CASE
    WHEN actual_end_date IS NULL THEN NULL
    WHEN actual_end_date::text ~ '^\d{8}$' THEN to_date(actual_end_date::text, 'YYYYMMDD')
    WHEN actual_end_date::text ~ '^\d{4}-\d{2}-\d{2}$' THEN actual_end_date::text::date
    ELSE NULL
  END AS actual_end_date,
  NULLIF(btrim(status), '') AS status,
  NULLIF(btrim(priority_level), '') AS priority_level,
  NULLIF(btrim(remark), '') AS remark
FROM {{ ref('pm_dim_subproject_seed') }}

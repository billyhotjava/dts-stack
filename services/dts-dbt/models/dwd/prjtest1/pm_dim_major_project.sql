{{ config(materialized='table', tags=['project-management', 'dim', 'project-cockpit', 'dwd']) }}

SELECT
  NULLIF(btrim(major_project_id), '') AS major_project_id,
  NULLIF(btrim(major_project_code), '') AS major_project_code,
  NULLIF(btrim(major_project_name), '') AS major_project_name,
  NULLIF(btrim(program_id), '') AS program_id,
  NULLIF(btrim(program_name), '') AS program_name,
  NULLIF(btrim(project_level), '') AS project_level,
  NULLIF(btrim(owner_dept), '') AS owner_dept,
  NULLIF(btrim(owner_leader), '') AS owner_leader,
  NULLIF(btrim(priority_level), '') AS priority_level,
  CASE WHEN start_date IS NULL THEN NULL ELSE start_date::date END AS start_date,
  CASE WHEN plan_end_date IS NULL THEN NULL ELSE plan_end_date::date END AS plan_end_date,
  NULLIF(btrim(status), '') AS status,
  NULLIF(btrim(remark), '') AS remark
FROM {{ ref('pm_dim_major_project_seed') }}

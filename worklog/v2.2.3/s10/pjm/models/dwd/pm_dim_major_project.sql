{{ config(materialized='table', tags=['project-management', 'dim', 'project-cockpit', 'dwd']) }}

-- 从 ODS 节点数据自动推导项目维度，无需 seed
-- project_no IS the major project grouping key (客户确认：项目编号代表项目名称)
WITH raw AS (
  SELECT
    {{ nullif_placeholder("o.project_no") }} AS project_no,
    {{ nullif_placeholder("o.project_no") }} AS major_project_name,
    {{ nullif_placeholder("o.dept") }} AS dept,
    {{ nullif_placeholder("o.dept_leader") }} AS dept_leader,
    {{ parse_date_safe("o.plan_date") }} AS plan_date
  FROM {{ source('pm_ods', 'project_subject_domain') }} o
  WHERE btrim(COALESCE(o.project_no, '')) != ''
),
agg AS (
  SELECT
    project_no,
    min(major_project_name) AS major_project_name,
    min(plan_date) AS start_date,
    max(plan_date) AS plan_end_date
  FROM raw
  GROUP BY project_no
),
first_dept AS (
  SELECT DISTINCT ON (project_no)
    project_no,
    dept,
    dept_leader
  FROM raw
  WHERE dept IS NOT NULL
  ORDER BY project_no, dept
)
SELECT
  a.project_no AS major_project_id,
  a.project_no AS major_project_code,
  a.major_project_name,
  '项目' AS project_level,
  d.dept AS owner_dept,
  d.dept_leader AS owner_leader,
  'A' AS priority_level,
  a.start_date,
  a.plan_end_date,
  '执行中' AS status,
  NULL::text AS remark
FROM agg a
LEFT JOIN first_dept d ON d.project_no = a.project_no

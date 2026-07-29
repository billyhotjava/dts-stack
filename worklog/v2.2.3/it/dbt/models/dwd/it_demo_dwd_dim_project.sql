{{ config(materialized='table', tags=['it-demo', 'dwd', 'dimension']) }}

SELECT
    cast(project_code AS varchar(32)) AS project_code,
    cast(project_name AS varchar(200)) AS project_name,
    cast(owner_org_code AS varchar(32)) AS owner_org_code,
    cast(manager_name AS varchar(100)) AS manager_name,
    cast(project_status_code AS varchar(32)) AS project_status_code,
    cast(plan_start_date AS date) AS plan_start_date,
    cast(plan_end_date AS date) AS plan_end_date
FROM {{ source('it_demo_ods', 'project') }}
WHERE project_code IS NOT NULL

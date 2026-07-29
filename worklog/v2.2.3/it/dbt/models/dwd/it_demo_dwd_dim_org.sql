{{ config(materialized='table', tags=['it-demo', 'dwd', 'dimension']) }}

SELECT
    cast(org_code AS varchar(32)) AS org_code,
    cast(org_name AS varchar(100)) AS org_name,
    cast(parent_org_code AS varchar(32)) AS parent_org_code,
    cast(org_level_code AS varchar(32)) AS org_level_code,
    cast(record_status_code AS varchar(32)) AS record_status_code
FROM {{ source('it_demo_ods', 'org') }}
WHERE org_code IS NOT NULL

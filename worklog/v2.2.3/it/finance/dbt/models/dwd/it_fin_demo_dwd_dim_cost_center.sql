{{ config(materialized='table', tags=['it-fin-demo', 'dwd', 'dimension']) }}

SELECT
    cast(cost_center_code AS varchar(32)) AS cost_center_code,
    cast(cost_center_name AS varchar(100)) AS cost_center_name,
    cast(parent_cost_center_code AS varchar(32)) AS parent_cost_center_code,
    cast(manager_name AS varchar(100)) AS manager_name,
    cast(record_status_code AS varchar(32)) AS record_status_code
FROM {{ source('it_fin_demo_ods', 'cost_center') }}
WHERE cost_center_code IS NOT NULL

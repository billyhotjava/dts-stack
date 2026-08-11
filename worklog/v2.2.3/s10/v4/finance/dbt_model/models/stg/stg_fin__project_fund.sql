{{ config(materialized='view', tags=['finance', 'stg', 'project-fund']) }}

SELECT
  md5(
    coalesce(cast(row_no as text), '')
    || '|'
    || coalesce(cast(project_id as text), '')
    || '|'
    || coalesce(cast(cycle as text), '')
  ) AS source_row_id,
  'ods_finance_project_fund'::text AS source_table,

  cast(row_no as integer) AS row_no,

  nullif(btrim(cast(project_id as text)), '') AS project_id,

  cast(cycle as text) AS cycle_raw,
  nullif(btrim(cast(cycle as text)), '') AS cycle,

  cast(total_fund as numeric(15,2)) AS total_fund,

  cast(is_major_project as boolean) AS is_major_project,

  nullif(btrim(cast(research_dept as text)), '') AS research_dept,

  cast(project_status as text) AS project_status_raw,
  nullif(btrim(cast(project_status as text)), '') AS project_status,

  cast(direct_ctrl as numeric(15,2)) AS direct_ctrl,
  cast(reserve_indirect as numeric(15,2)) AS reserve_indirect,
  cast(direct_spent as numeric(15,2)) AS direct_spent,
  cast(direct_rate as numeric(8,2)) AS direct_rate,
  cast(indirect_spent as numeric(15,2)) AS indirect_spent,
  cast(received_fund as numeric(15,2)) AS received_fund,
  cast(receivable_fund as numeric(15,2)) AS receivable_fund
FROM {{ source('fin_ods', 'project_fund') }}
WHERE nullif(btrim(cast(project_id as text)), '') IS NOT NULL

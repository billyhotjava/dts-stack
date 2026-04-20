{{ config(materialized='view', tags=['finance', 'stg', 'project-fund']) }}

SELECT
  md5(
    coalesce(cast(project_id as text), '')
    || '|'
    || coalesce(cast(cycle as text), '')
  ) AS source_row_id,
  'ods_finance_project_fund'::text AS source_table,

  {{ parse_numeric_safe("row_no") }}::int AS row_no,

  {{ nullif_placeholder("project_id") }} AS project_id,

  cast(cycle as text) AS cycle_raw,
  {{ nullif_placeholder("cycle") }} AS cycle,

  {{ parse_numeric_safe("total_fund") }}::numeric(15,2) AS total_fund,

  is_major_project,

  {{ nullif_placeholder("research_dept") }} AS research_dept,

  cast(project_status as text) AS project_status_raw,
  nullif(btrim(cast(project_status as text)), '') AS project_status,

  {{ parse_numeric_safe("direct_ctrl") }}::numeric(15,2) AS direct_ctrl,
  {{ parse_numeric_safe("reserve_indirect") }}::numeric(15,2) AS reserve_indirect,
  {{ parse_numeric_safe("direct_spent") }}::numeric(15,2) AS direct_spent,
  {{ parse_numeric_safe("direct_rate") }}::numeric(8,2) AS direct_rate,
  {{ parse_numeric_safe("indirect_spent") }}::numeric(15,2) AS indirect_spent,
  {{ parse_numeric_safe("received_fund") }}::numeric(15,2) AS received_fund,
  {{ parse_numeric_safe("receivable_fund") }}::numeric(15,2) AS receivable_fund
FROM {{ source('fin_ods', 'project_fund') }}
WHERE project_id IS NOT NULL

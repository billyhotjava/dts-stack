{{ config(materialized='view', tags=['finance', 'stg', 'project-fund']) }}

WITH cleaned AS (
  SELECT
    md5(
      coalesce(cast(project_id as text), '')
      || '|'
      || coalesce(cast(cycle as text), '')
    ) AS source_row_id,
    'ods_finance_project_fund'::text AS source_table,
    {{ nullif_placeholder("project_id") }} AS project_id,
    cast(cycle as text) AS cycle_raw,
    {{ nullif_placeholder("cycle") }} AS cycle,
    {{ parse_numeric_safe("total_fund") }}::numeric(15,2) AS total_fund,
    {{ parse_numeric_safe("direct_ctrl") }}::numeric(15,2) AS direct_ctrl,
    {{ parse_numeric_safe("reserve_indirect") }}::numeric(15,2) AS reserve_indirect,
    {{ parse_numeric_safe("direct_rate") }}::numeric(8,2) AS direct_rate,
    {{ parse_numeric_safe("indirect_spent") }}::numeric(15,2) AS indirect_spent
  FROM {{ source('fin_ods', 'project_fund') }}
),
normalized AS (
  SELECT
    c.*,
    CASE
      WHEN c.cycle ~ '^\d{4}\.\d{2}-\d{4}\.\d{2}$'
      THEN to_date(replace(split_part(c.cycle, '-', 1), '.', '') || '01', 'YYYYMMDD')
    END AS cycle_start_date,
    CASE
      WHEN c.cycle ~ '^\d{4}\.\d{2}-\d{4}\.\d{2}$'
      THEN to_date(replace(split_part(c.cycle, '-', 2), '.', '') || '01', 'YYYYMMDD')
    END AS cycle_end_date
  FROM cleaned c
)

SELECT
  n.source_row_id,
  n.source_table,
  n.project_id,
  n.cycle_raw,
  n.cycle,
  n.total_fund,
  n.direct_ctrl,
  n.reserve_indirect,
  n.direct_rate,
  n.indirect_spent,
  n.cycle_start_date,
  n.cycle_end_date,
  to_char(n.cycle_start_date, 'YYYY-MM') AS cycle_start_month,
  to_char(n.cycle_end_date, 'YYYY-MM') AS cycle_end_month,
  CASE
    WHEN n.cycle_start_date IS NOT NULL AND n.cycle_end_date IS NOT NULL THEN
      (
        (extract(year from n.cycle_end_date)::int - extract(year from n.cycle_start_date)::int) * 12
        + (extract(month from n.cycle_end_date)::int - extract(month from n.cycle_start_date)::int)
        + 1
      )
  END AS cycle_month_span
FROM normalized n
WHERE n.project_id IS NOT NULL

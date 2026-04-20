{{ config(materialized='table', tags=['finance', 'biz', 'dwd', 'project-fund']) }}

WITH stg AS (
  SELECT * FROM {{ ref('stg_fin__project_fund') }}
),
parsed AS (
  SELECT
    s.*,
    CASE
      WHEN s.cycle ~ '^\d{4}\.\d{2}-\d{4}\.\d{2}$'
      THEN to_date(replace(split_part(s.cycle, '-', 1), '.', '') || '01', 'YYYYMMDD')
    END AS cycle_start_date,
    CASE
      WHEN s.cycle ~ '^\d{4}\.\d{2}-\d{4}\.\d{2}$'
      THEN to_date(replace(split_part(s.cycle, '-', 2), '.', '') || '01', 'YYYYMMDD')
    END AS cycle_end_date
  FROM stg s
),
derived AS (
  SELECT
    p.*,
    to_char(p.cycle_start_date, 'YYYY-MM') AS cycle_start_month,
    to_char(p.cycle_end_date, 'YYYY-MM') AS cycle_end_month,
    CASE
      WHEN p.cycle_start_date IS NOT NULL AND p.cycle_end_date IS NOT NULL THEN
        (extract(year from p.cycle_end_date)::int - extract(year from p.cycle_start_date)::int) * 12
        + (extract(month from p.cycle_end_date)::int - extract(month from p.cycle_start_date)::int)
        + 1
    END AS cycle_month_span,
    round(coalesce(p.direct_ctrl, 0) * coalesce(p.direct_rate, 0) / 100, 2)::numeric(15,2) AS direct_spent
  FROM parsed p
),
final AS (
  SELECT
    d.*,
    (d.direct_spent + coalesce(d.indirect_spent, 0))::numeric(15,2) AS total_spent,
    greatest(
      coalesce(d.total_fund, 0) - (d.direct_spent + coalesce(d.indirect_spent, 0)),
      0
    )::numeric(15,2) AS remaining_fund,
    CASE
      WHEN coalesce(d.total_fund, 0) > 0 THEN
        round(
          (d.direct_spent + coalesce(d.indirect_spent, 0)) * 100.0 / d.total_fund,
          1
        )
      ELSE 0
    END AS total_rate,
    CASE
      WHEN coalesce(d.reserve_indirect, 0) > 0 THEN
        round(coalesce(d.indirect_spent, 0) * 100.0 / d.reserve_indirect, 1)
      ELSE 0
    END AS indirect_rate,
    CASE WHEN coalesce(d.direct_rate, 0) > 100 THEN true ELSE false END AS is_direct_rate_over_100
  FROM derived d
)

SELECT
  concat('project_fund:', f.project_id, ':', coalesce(f.cycle, 'unknown')) AS project_fund_id,

  f.source_row_id,
  f.source_table,
  f.project_id,
  f.cycle_raw,
  f.cycle,

  f.cycle_start_date,
  f.cycle_end_date,
  f.cycle_start_month,
  f.cycle_end_month,
  f.cycle_month_span,

  f.total_fund,
  f.direct_ctrl,
  f.reserve_indirect,
  f.direct_rate,
  f.indirect_spent,

  f.direct_spent,
  f.total_spent,
  f.remaining_fund,
  f.total_rate,
  f.indirect_rate,

  f.is_direct_rate_over_100,
  CASE WHEN f.total_rate > 100 THEN true ELSE false END AS is_total_rate_over_100,

  now() AS etl_time
FROM final f

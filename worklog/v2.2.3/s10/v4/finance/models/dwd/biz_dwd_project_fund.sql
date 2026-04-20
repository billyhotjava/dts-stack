{{ config(materialized='table', tags=['finance', 'biz', 'dwd', 'project-fund']) }}

SELECT
  concat('project_fund:', t.project_id, ':', coalesce(t.cycle, 'unknown')) AS project_fund_id,
  t.source_row_id,
  t.source_table,
  t.project_id,
  t.cycle_raw,
  t.cycle,
  t.cycle_start_date,
  t.cycle_end_date,
  t.cycle_start_month,
  t.cycle_end_month,
  t.cycle_month_span,
  t.total_fund,
  t.direct_ctrl,
  t.reserve_indirect,
  t.direct_rate,
  t.indirect_spent,
  round(coalesce(t.direct_ctrl, 0) * coalesce(t.direct_rate, 0) / 100, 2) AS direct_spent,
  round(coalesce(t.direct_ctrl, 0) * coalesce(t.direct_rate, 0) / 100, 2)
    + coalesce(t.indirect_spent, 0) AS total_spent,
  CASE
    WHEN coalesce(t.total_fund, 0) > 0 THEN
      round(
        (
          round(coalesce(t.direct_ctrl, 0) * coalesce(t.direct_rate, 0) / 100, 2)
          + coalesce(t.indirect_spent, 0)
        ) * 100.0 / t.total_fund,
      1)
    ELSE 0
  END AS total_rate,
  CASE
    WHEN coalesce(t.reserve_indirect, 0) > 0 THEN
      round(coalesce(t.indirect_spent, 0) * 100.0 / t.reserve_indirect, 1)
    ELSE 0
  END AS indirect_rate,
  greatest(
    coalesce(t.total_fund, 0)
    - (
      round(coalesce(t.direct_ctrl, 0) * coalesce(t.direct_rate, 0) / 100, 2)
      + coalesce(t.indirect_spent, 0)
    ),
    0
  ) AS remaining_fund,
  CASE
    WHEN coalesce(t.direct_rate, 0) > 100 THEN true
    ELSE false
  END AS is_direct_rate_over_100,
  CASE
    WHEN coalesce(t.total_fund, 0) > 0
      AND (
        (
          round(coalesce(t.direct_ctrl, 0) * coalesce(t.direct_rate, 0) / 100, 2)
          + coalesce(t.indirect_spent, 0)
        ) * 100.0 / t.total_fund
      ) > 100 THEN true
    ELSE false
  END AS is_total_rate_over_100,
  now() AS etl_time
FROM {{ ref('stg_fin__project_fund') }} t

{{ config(materialized='table', tags=['finance', 'biz', 'dwd', 'own-fund']) }}

WITH stg AS (
  SELECT * FROM {{ ref('stg_fin__own_fund') }}
),
normalized AS (
  SELECT
    s.*,
    substring(s.year_period from '^\d{4}')::int AS period_year,
    ys.period_type_code AS period_type,
    ys.sort_order AS period_sort
  FROM stg s
  LEFT JOIN {{ ref('dim_year_period_suffix') }} ys
    ON s.year_period LIKE '%' || ys.suffix
),
derived AS (
  SELECT
    n.*,
    (
      coalesce(n.career_fund, 0)
      + coalesce(n.deprec_fund, 0)
      + coalesce(n.welfare_fund, 0)
      + coalesce(n.safety_fund, 0)
    )::numeric(15,2) AS total_recalc,
    (
      coalesce(n.total, 0)
      - (
        coalesce(n.career_fund, 0)
        + coalesce(n.deprec_fund, 0)
        + coalesce(n.welfare_fund, 0)
        + coalesce(n.safety_fund, 0)
      )
    )::numeric(15,2) AS total_gap,
    CASE
      WHEN n.period_type = 'balance' THEN true
      ELSE false
    END AS is_balance_row
  FROM normalized n
)

SELECT
  concat(
    'own_fund:',
    coalesce(d.period_year::text, 'unknown'),
    ':',
    coalesce(d.period_type, 'unknown')
  ) AS own_fund_id,

  d.source_row_id,
  d.source_table,
  d.year_period_raw,
  d.year_period,
  d.period_year,

  d.period_type,
  pt.period_type_id,
  pt.label AS period_type_label,
  d.period_sort,
  d.is_balance_row,

  d.career_fund,
  d.career_note,
  d.deprec_fund,
  d.deprec_note,
  d.welfare_fund,
  d.safety_fund,
  d.total,
  d.total_recalc,
  d.total_gap,
  CASE WHEN abs(d.total_gap) <= 0.01 THEN true ELSE false END AS is_total_balanced,

  now() AS etl_time
FROM derived d
LEFT JOIN {{ ref('dim_own_fund_period_type') }} pt
  ON pt.code = d.period_type
WHERE d.period_year IS NOT NULL
  AND d.period_type IS NOT NULL

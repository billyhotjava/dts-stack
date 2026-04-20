

SELECT
  concat('own_fund:', coalesce(t.period_year::text, 'unknown'), ':', coalesce(t.period_type, 'unknown')) AS own_fund_id,
  t.source_row_id,
  t.source_table,
  t.year_period_raw,
  t.year_period,
  t.period_year,
  t.period_type,
  p.period_type_id,
  coalesce(t.period_sort, p.sort_order) AS period_sort,
  t.is_balance_row,
  t.career_fund,
  t.career_note,
  t.deprec_fund,
  t.deprec_note,
  t.welfare_fund,
  t.safety_fund,
  t.total,
  coalesce(t.career_fund, 0)
    + coalesce(t.deprec_fund, 0)
    + coalesce(t.welfare_fund, 0)
    + coalesce(t.safety_fund, 0) AS total_recalc,
  (
    coalesce(t.total, 0)
    - (
      coalesce(t.career_fund, 0)
      + coalesce(t.deprec_fund, 0)
      + coalesce(t.welfare_fund, 0)
      + coalesce(t.safety_fund, 0)
    )
  )::numeric(15,2) AS total_gap,
  CASE
    WHEN abs(
      coalesce(t.total, 0)
      - (
        coalesce(t.career_fund, 0)
        + coalesce(t.deprec_fund, 0)
        + coalesce(t.welfare_fund, 0)
        + coalesce(t.safety_fund, 0)
      )
    ) <= 0.01 THEN true
    ELSE false
  END AS is_total_balanced,
  now() AS etl_time
FROM "biadmin"."public"."stg_fin__own_fund" t
LEFT JOIN "biadmin"."public"."dim_own_fund_period_type" p
  ON p.code = t.period_type
WHERE t.period_year IS NOT NULL
  AND t.period_type IS NOT NULL
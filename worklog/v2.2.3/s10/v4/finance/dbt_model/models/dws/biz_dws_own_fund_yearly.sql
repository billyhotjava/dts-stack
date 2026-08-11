{{ config(materialized='table', tags=['finance', 'biz', 'dws', 'own-fund']) }}

-- 年度基金汇总表：按 年度 × 基金类别 透视
-- 派生: derived_balance = opening + increase - usage
-- 同时产出 fund_category='全部' 的年度总量行，便于 ADS 直接消费

WITH base AS (
  SELECT * FROM {{ ref('biz_dwd_own_fund') }}
),
pivot_by_category AS (
  SELECT
    year_num,
    fund_category_code,
    fund_category_label,
    fund_category_sort,
    coalesce(sum(CASE WHEN fund_source_code = 'OPENING'          THEN amount ELSE 0 END), 0)::numeric(15,2) AS opening_amount,
    coalesce(sum(CASE WHEN fund_source_code = 'PLANNED_INCREASE' THEN amount ELSE 0 END), 0)::numeric(15,2) AS increase_amount,
    coalesce(sum(CASE WHEN fund_source_code = 'PLANNED_USAGE'    THEN amount ELSE 0 END), 0)::numeric(15,2) AS usage_amount,
    count(DISTINCT fund_source_code) AS source_count,
    count(*) AS record_count
  FROM base
  GROUP BY year_num, fund_category_code, fund_category_label, fund_category_sort
),
year_totals AS (
  SELECT
    year_num,
    'ALL'::text AS fund_category_code,
    '全部基金'::text AS fund_category_label,
    99::int AS fund_category_sort,
    coalesce(sum(opening_amount), 0)::numeric(15,2) AS opening_amount,
    coalesce(sum(increase_amount), 0)::numeric(15,2) AS increase_amount,
    coalesce(sum(usage_amount), 0)::numeric(15,2) AS usage_amount,
    min(source_count) AS source_count,
    sum(record_count) AS record_count
  FROM pivot_by_category
  GROUP BY year_num
),
unioned AS (
  SELECT * FROM pivot_by_category
  UNION ALL
  SELECT * FROM year_totals
),
metrics AS (
  SELECT
    year_num,
    fund_category_code,
    fund_category_label,
    fund_category_sort,
    opening_amount,
    increase_amount,
    usage_amount,
    (opening_amount + increase_amount - usage_amount)::numeric(15,2) AS derived_balance,
    source_count,
    record_count,
    CASE
      WHEN source_count = 3 THEN true
      ELSE false
    END AS is_complete_sources,
    CASE
      WHEN (opening_amount + increase_amount) > 0 THEN
        round(usage_amount * 100.0 / (opening_amount + increase_amount), 2)
      ELSE 0
    END AS usage_rate,
    CASE
      WHEN opening_amount > 0 THEN
        round(
          ((opening_amount + increase_amount - usage_amount) - opening_amount) * 100.0 / opening_amount,
          2
        )
      ELSE 0
    END AS net_change_rate
  FROM unioned
)

SELECT
  m.year_num,
  m.fund_category_code,
  m.fund_category_label,
  m.fund_category_sort,
  m.opening_amount,
  m.increase_amount,
  m.usage_amount,
  m.derived_balance,
  m.source_count,
  m.record_count,
  m.is_complete_sources,
  m.usage_rate,
  m.net_change_rate,
  CASE
    WHEN lag(m.derived_balance) OVER (
      PARTITION BY m.fund_category_code
      ORDER BY m.year_num
    ) > 0 THEN
      round(
        (
          m.derived_balance
          - lag(m.derived_balance) OVER (
            PARTITION BY m.fund_category_code
            ORDER BY m.year_num
          )
        ) * 100.0
        / lag(m.derived_balance) OVER (
          PARTITION BY m.fund_category_code
          ORDER BY m.year_num
        ),
        2
      )
    ELSE null
  END AS year_over_year_growth_rate,
  now() AS etl_time
FROM metrics m
ORDER BY m.year_num, m.fund_category_sort

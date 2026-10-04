{{ config(materialized='table', tags=['finance', 'biz', 'dwd', 'own-fund']) }}

-- 年度基金事实表：长表结构
-- 每行 = (年度, 基金来源, 基金类别, 金额)
-- 基金来源: 年初 / 预计增加 / 预计使用（ODS 不含"余额"，年末余额由 DWS 派生）
-- 基金类别: 事业基金 / 职工福利基金 / 安全生产基金

WITH stg AS (
  SELECT * FROM {{ ref('stg_fin__own_fund') }}
),
joined AS (
  SELECT
    s.*,
    fs.fund_source_id,
    fs.code        AS fund_source_code,
    fs.label       AS fund_source_label,
    fs.sort_order  AS fund_source_sort,
    fs.is_opening,
    fs.is_increase,
    fs.is_usage,
    fc.fund_category_id,
    fc.code        AS fund_category_code,
    fc.label       AS fund_category_label,
    fc.sort_order  AS fund_category_sort,
    fc.is_career,
    fc.is_welfare,
    fc.is_safety
  FROM stg s
  LEFT JOIN {{ ref('dim_fund_source') }} fs
    ON fs.raw_value = s.fund_source
  LEFT JOIN {{ ref('dim_fund_category') }} fc
    ON fc.raw_value = s.fund_category
)

SELECT
  concat(
    'own_fund:',
    coalesce(year_num::text, 'unknown'),
    ':',
    coalesce(fund_source_code, 'unknown'),
    ':',
    coalesce(fund_category_code, 'unknown')
  ) AS own_fund_id,

  source_row_id,
  source_table,

  year_num,

  fund_source_raw,
  fund_source,
  fund_source_id,
  fund_source_code,
  fund_source_label,
  fund_source_sort,
  is_opening,
  is_increase,
  is_usage,

  fund_category_raw,
  fund_category,
  fund_category_id,
  fund_category_code,
  fund_category_label,
  fund_category_sort,
  is_career,
  is_welfare,
  is_safety,

  amount,
  note,

  now() AS etl_time
FROM joined
WHERE fund_source_code IS NOT NULL
  AND fund_category_code IS NOT NULL

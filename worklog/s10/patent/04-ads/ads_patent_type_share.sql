-- ============================================================
-- 模型: ads_patent_type_share
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 当年各专利类型的申请数量及占比，供仪表盘饼图/环形图使用。
--
-- 计算逻辑：
--   - 筛选当年数据
--   - 使用窗口函数 SUM() OVER() 计算总量
--   - share = 该类型数量 / 总量
--
-- 字段说明：
--   - stat_year:   当年年份
--   - patent_type: 专利类型
--   - apply_cnt:   申请数量
--   - share:       占比（0~1 之间的小数，4 位精度）
-- ============================================================

{{ config(materialized='table', alias='ads_patent_type_share', schema='public', tags=['ads', 'patent']) }}

WITH params AS (
  SELECT NULLIF(current_setting('dts.report_year', true), '')::int AS yr
)
SELECT
  stat_year,
  patent_type,
  apply_cnt,
  CASE
    WHEN SUM(apply_cnt) OVER (PARTITION BY stat_year) = 0 THEN 0
    ELSE ROUND(apply_cnt::numeric / SUM(apply_cnt) OVER (PARTITION BY stat_year)::numeric, 4)
  END AS share

FROM {{ ref('dws_patent_year_type') }}
WHERE (SELECT yr FROM params) IS NULL OR stat_year = (SELECT yr FROM params)

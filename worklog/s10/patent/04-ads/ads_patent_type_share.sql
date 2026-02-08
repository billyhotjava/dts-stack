-- ============================================================
-- 模型: ads_patent_type_share
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 各年度各专利类型的申请数量及占比，供仪表盘饼图/环形图使用。
--
-- 计算逻辑：
--   - 保留所有年份数据
--   - 使用窗口函数 SUM() OVER(PARTITION BY stat_year) 按年计算总量
--   - share = 该类型数量 / 该年总量
--
-- 查询示例：
--   SELECT * FROM ads_patent_type_share WHERE stat_year = 2025
-- ============================================================

{{ config(materialized='table', alias='ads_patent_type_share', schema='public', tags=['ads', 'patent']) }}

SELECT
  stat_year,
  patent_type,
  apply_cnt,
  CASE
    WHEN SUM(apply_cnt) OVER (PARTITION BY stat_year) = 0 THEN 0
    ELSE ROUND(apply_cnt::numeric / SUM(apply_cnt) OVER (PARTITION BY stat_year)::numeric, 4)
  END AS share

FROM {{ ref('dws_patent_year_type') }}
ORDER BY stat_year, apply_cnt DESC

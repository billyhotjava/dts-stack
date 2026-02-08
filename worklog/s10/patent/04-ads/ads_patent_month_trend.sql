-- ============================================================
-- 模型: ads_patent_month_trend
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 全量月度受理/授权趋势数据，供仪表盘折线图使用。
--
-- 保留所有年份数据，前端按 stat_year 过滤当年。
--
-- 查询示例：
--   SELECT * FROM ads_patent_month_trend WHERE stat_year = 2025
-- ============================================================

{{ config(materialized='table', alias='ads_patent_month_trend', schema='public', tags=['ads', 'patent']) }}

SELECT
  stat_year,
  stat_month,
  accepted_cnt,
  granted_cnt

FROM {{ ref('dws_patent_month_trend') }}
ORDER BY stat_year, stat_month

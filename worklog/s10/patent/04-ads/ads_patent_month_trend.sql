-- ============================================================
-- 模型: ads_patent_month_trend
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 当年月度受理/授权趋势数据，供仪表盘折线图使用。
--
-- 与 DWS 层 dws_patent_month_trend 的区别：
--   - DWS 保留所有年份的月度数据（历史全量）
--   - ADS 只保留当年数据，减少前端数据量
--   - ADS 按月份排序，前端可直接渲染
--
-- 字段说明：
--   - stat_year:    当年年份
--   - stat_month:   月份（YYYY-MM）
--   - accepted_cnt: 当月受理数
--   - granted_cnt:  当月授权数
-- ============================================================

{{ config(materialized='table', alias='ads_patent_month_trend', schema='public', tags=['ads', 'patent']) }}

WITH params AS (
  SELECT NULLIF(current_setting('dts.report_year', true), '')::int AS yr
)
SELECT
  stat_year,
  stat_month,
  accepted_cnt,
  granted_cnt

FROM {{ ref('dws_patent_month_trend') }}
WHERE (SELECT yr FROM params) IS NULL OR stat_year = (SELECT yr FROM params)
ORDER BY stat_year, stat_month

-- ============================================================
-- 模型: dws_patent_month_trend
-- 层级: DWS (汇总层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 按月度统计的受理/授权数量趋势表。
-- 每行代表某年某月的受理数和授权数。
--
-- 为什么需要这张表：
--   - 仪表盘需要展示"月度趋势折线图"（受理 vs 授权双线）
--   - 受理数按申请月统计，授权数按授权月统计（两个不同的时间维度）
--   - 需要 UNION ALL 合并后按月聚合，避免同一个月份出现两行
--
-- 聚合逻辑：
--   第一个子查询：按 application_month 统计受理数
--   第二个子查询：按 grant_month 统计授权数
--   外层：按 (stat_year, stat_month) 聚合 SUM，合并两个维度的数据
--
-- 字段说明：
--   - stat_year:    年份
--   - stat_month:   月份（YYYY-MM 格式）
--   - accepted_cnt: 该月受理数量
--   - granted_cnt:  该月授权数量
-- ============================================================

{{ config(materialized='table', alias='dws_patent_month_trend', schema='public', tags=['dws', 'patent']) }}

SELECT
  stat_year,
  stat_month,
  SUM(accepted_cnt) AS accepted_cnt,
  SUM(granted_cnt)  AS granted_cnt
FROM (
  -- 受理数：按申请月统计
  SELECT
    application_year  AS stat_year,
    application_month AS stat_month,
    SUM(CASE WHEN is_accepted THEN 1 ELSE 0 END) AS accepted_cnt,
    0 AS granted_cnt
  FROM {{ ref('dwd_patent') }}
  WHERE application_year IS NOT NULL
    AND application_month IS NOT NULL
  GROUP BY application_year, application_month

  UNION ALL

  -- 授权数：按授权月统计
  SELECT
    grant_year  AS stat_year,
    grant_month AS stat_month,
    0 AS accepted_cnt,
    COUNT(*) AS granted_cnt
  FROM {{ ref('dwd_patent') }}
  WHERE grant_year IS NOT NULL
    AND grant_month IS NOT NULL
  GROUP BY grant_year, grant_month
) sub
GROUP BY stat_year, stat_month

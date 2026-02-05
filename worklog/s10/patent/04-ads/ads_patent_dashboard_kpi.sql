-- ============================================================
-- 模型: ads_patent_dashboard_kpi
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 仪表盘首页核心 KPI 卡片数据。只保留当年一行记录。
--
-- 展示指标：
--   - 今年申请总量
--   - 去年申请总量
--   - 同比增长率
--   - 今年受理数
--   - 今年授权数（授权年口径）
--   - 今年授权率
--
-- 为什么单独建 ADS 表：
--   - 仪表盘加载时只需 SELECT * FROM ads_patent_dashboard_kpi WHERE stat_year = 2026
--   - 避免前端实时做同比计算（需要跨行关联）
--   - 计算逻辑固化在 SQL 中，保证口径一致
--
-- 数据源：dws_patent_year_kpi
-- 关联方式：当年 LEFT JOIN 上一年，计算同比
-- 授权数来源：dws_patent_year_grant（授权年口径）
-- ============================================================

{{ config(materialized='table', alias='ads_patent_dashboard_kpi', schema='public', tags=['ads', 'patent']) }}

WITH this AS (
  SELECT EXTRACT(YEAR FROM current_date)::int AS yr
)
SELECT
  this.yr                                          AS stat_year,

  COALESCE(k1.apply_cnt, 0)                       AS this_year_apply_cnt,
  COALESCE(k0.apply_cnt, 0)                       AS last_year_apply_cnt,

  CASE
    WHEN COALESCE(k0.apply_cnt, 0) = 0 THEN 0
    ELSE ROUND(
      (COALESCE(k1.apply_cnt, 0) - k0.apply_cnt)::numeric
      / k0.apply_cnt::numeric, 4
    )
  END                                              AS yoy_growth_rate,

  COALESCE(k1.accepted_cnt, 0)                    AS this_year_accepted_cnt,
  COALESCE(g1.granted_cnt, 0)                     AS this_year_granted_cnt,
  COALESCE(k1.grant_rate_app, 0)                  AS this_year_grant_rate

FROM this
LEFT JOIN {{ ref('dws_patent_year_kpi') }} k1 ON k1.stat_year = this.yr
LEFT JOIN {{ ref('dws_patent_year_kpi') }} k0 ON k0.stat_year = this.yr - 1
LEFT JOIN {{ ref('dws_patent_year_grant') }} g1 ON g1.stat_year = this.yr

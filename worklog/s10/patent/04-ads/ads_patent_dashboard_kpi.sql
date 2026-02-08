-- ============================================================
-- 模型: ads_patent_dashboard_kpi
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 仪表盘首页核心 KPI 卡片数据。每年一行，含同比增长率。
--
-- 展示指标：
--   - 当年申请总量
--   - 上年申请总量
--   - 同比增长率
--   - 当年受理数
--   - 当年授权数（授权年口径）
--   - 当年授权率
--
-- 数据源：dws_patent_year_kpi + dws_patent_year_grant
-- 关联方式：当年 LEFT JOIN 上一年，计算同比
--
-- 查询示例：
--   SELECT * FROM ads_patent_dashboard_kpi WHERE stat_year = 2025
-- ============================================================

{{ config(materialized='table', alias='ads_patent_dashboard_kpi', schema='public', tags=['ads', 'patent']) }}

SELECT
  k1.stat_year                                     AS stat_year,

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

FROM {{ ref('dws_patent_year_kpi') }} k1
LEFT JOIN {{ ref('dws_patent_year_kpi') }} k0 ON k0.stat_year = k1.stat_year - 1
LEFT JOIN {{ ref('dws_patent_year_grant') }} g1 ON g1.stat_year = k1.stat_year
ORDER BY k1.stat_year

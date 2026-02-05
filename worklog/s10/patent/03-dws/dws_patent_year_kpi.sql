-- ============================================================
-- 模型: dws_patent_year_kpi
-- 层级: DWS (汇总层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 按申请年度汇总的核心 KPI 指标表。
-- 每行代表一个自然年的专利申请统计。
--
-- 为什么需要这张表：
--   - 仪表盘首页需要展示"本年申请量"、"授权率"等核心 KPI
--   - 同比计算需要跨年度对比，需要每年都有一行汇总数据
--   - ADS 层的 ads_patent_dashboard_kpi 直接关联本表做同比
--
-- 聚合口径：
--   - apply_cnt:       按 application_year 统计的申请总量
--   - accepted_cnt:    其中已受理（is_accepted=true）的数量
--   - granted_cnt_app: 其中已授权（is_granted=true）的数量（申请年口径）
--   - grant_rate_app:  授权率 = granted_cnt_app / apply_cnt
--
-- 注意：授权率以"申请年口径"计算，即"本年申请的专利中有多少已授权"，
-- 而非"本年授权的专利数量"。这是科技管理中更常用的统计口径。
-- ============================================================

{{ config(materialized='table', alias='dws_patent_year_kpi', schema='public', tags=['dws', 'patent']) }}

SELECT
  application_year AS stat_year,

  COUNT(*) AS apply_cnt,

  SUM(CASE WHEN is_accepted THEN 1 ELSE 0 END) AS accepted_cnt,

  SUM(CASE WHEN is_granted THEN 1 ELSE 0 END) AS granted_cnt_app,

  CASE
    WHEN COUNT(*) = 0 THEN 0
    ELSE ROUND(
      SUM(CASE WHEN is_granted THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4
    )
  END AS grant_rate_app

FROM {{ ref('dwd_patent') }}
WHERE application_year IS NOT NULL
GROUP BY application_year

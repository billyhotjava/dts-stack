-- ============================================================
-- 模型: dws_patent_year_grant
-- 层级: DWS (汇总层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 按授权年度汇总的授权数量统计表。
-- 每行代表一个自然年的授权数量（授权年口径）。
--
-- 为什么需要这张表：
--   - 指标“本年度授权专利数量”应以授权年统计，而非申请年
--   - 便于与申请年口径的数据分开对比，避免混淆
--
-- 字段说明：
--   - stat_year: 授权年份
--   - granted_cnt: 当年授权数量
-- ============================================================

{{ config(materialized='table', alias='dws_patent_year_grant', schema='public', tags=['dws', 'patent']) }}

SELECT
  grant_year AS stat_year,
  COUNT(*)   AS granted_cnt

FROM {{ ref('dwd_patent') }}
WHERE grant_year IS NOT NULL
  AND is_granted = true
GROUP BY grant_year

-- ============================================================
-- 模型: ads_patent_dept_rank
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 每年部门申请量排名 TOP20，供仪表盘横向柱状图使用。
--
-- 排名规则：
--   - 按年分区，每年独立排名
--   - 同年内按申请数量降序
--   - 数量相同时按部门名称字典序（保证排名稳定性）
--   - 每年只保留 TOP 20
--
-- 查询示例：
--   SELECT * FROM ads_patent_dept_rank WHERE stat_year = 2025
-- ============================================================

{{ config(materialized='table', alias='ads_patent_dept_rank', schema='public', tags=['ads', 'patent']) }}

WITH ranked AS (
  SELECT
    stat_year,
    dept_name,
    apply_cnt,
    ROW_NUMBER() OVER (PARTITION BY stat_year ORDER BY apply_cnt DESC, dept_name) AS rank_no
  FROM {{ ref('dws_patent_year_dept') }}
)
SELECT stat_year, dept_name, apply_cnt, rank_no
FROM ranked
WHERE rank_no <= 20
ORDER BY stat_year, rank_no

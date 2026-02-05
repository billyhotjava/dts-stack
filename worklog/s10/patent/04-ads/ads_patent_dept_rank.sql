-- ============================================================
-- 模型: ads_patent_dept_rank
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 当年部门申请量排名 TOP20，供仪表盘横向柱状图使用。
--
-- 为什么限制 TOP20：
--   - 部门数量可能很多，柱状图展示过多条目影响可读性
--   - 20 条足够覆盖主要贡献部门
--   - 如需全部数据，可直接查询 dws_patent_year_dept
--
-- 排名规则：
--   - 按申请数量降序
--   - 数量相同时按部门名称字典序（保证排名稳定性）
--
-- 字段说明：
--   - stat_year: 当年年份
--   - dept_name: 部门名称
--   - dept_code: 部门编码
--   - apply_cnt: 申请数量
--   - rank_no:   排名序号（1 = 最多）
-- ============================================================

{{ config(materialized='table', alias='ads_patent_dept_rank', schema='public', tags=['ads', 'patent']) }}

WITH params AS (
  SELECT NULLIF(current_setting('dts.report_year', true), '')::int AS yr
),
ranked AS (
  SELECT
    stat_year,
    dept_name,
    dept_code,
    apply_cnt,
    ROW_NUMBER() OVER (PARTITION BY stat_year ORDER BY apply_cnt DESC, dept_name) AS rank_no
  FROM {{ ref('dws_patent_year_dept') }}
  WHERE (SELECT yr FROM params) IS NULL OR stat_year = (SELECT yr FROM params)
)
SELECT *
FROM ranked
WHERE rank_no <= 20
ORDER BY stat_year, rank_no

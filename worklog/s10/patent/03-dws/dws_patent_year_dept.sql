-- ============================================================
-- 模型: dws_patent_year_dept
-- 层级: DWS (汇总层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 按申请年度 + 部门的交叉统计表。
--
-- 为什么需要这张表：
--   - 仪表盘需要展示"部门申请量排名"柱状图
--   - 不同年度的排名对比需要每年每部门一行数据
--   - ADS 层的 ads_patent_dept_rank 基于本表取 TOP20
--
-- 字段说明：
--   - stat_year: 申请年份
--   - dept_name: 部门名称（空值归为"未知"）
--   - apply_cnt: 该年度该部门的申请数量
-- ============================================================

{{ config(materialized='table', alias='dws_patent_year_dept', schema='public', tags=['dws', 'patent']) }}

SELECT
  application_year                              AS stat_year,
  COALESCE(NULLIF(dept_name, ''), '未知')       AS dept_name,
  COUNT(*)                                      AS apply_cnt

FROM {{ ref('dwd_patent') }}
WHERE application_year IS NOT NULL
GROUP BY application_year, COALESCE(NULLIF(dept_name, ''), '未知')

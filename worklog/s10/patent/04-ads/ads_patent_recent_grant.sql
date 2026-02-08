-- ============================================================
-- 模型: ads_patent_recent_grant
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 全量授权记录，按授权年+日期排序。
-- 供仪表盘"最新授权"表格使用。
--
-- 直接从 DWD 层取数（不经过 DWS），因为：
--   - 这是明细列表，不是聚合统计
--   - 前端按 grant_year 过滤展示
--
-- 查询示例：
--   SELECT * FROM ads_patent_recent_grant WHERE grant_year = 2025
-- ============================================================

{{ config(materialized='table', alias='ads_patent_recent_grant', schema='public', tags=['ads', 'patent']) }}

SELECT
  grant_year,
  grant_date,
  patent_no,
  patent_title_cn,
  assignee_name,
  dept_name,
  agent_org_name

FROM {{ ref('dwd_patent') }}
WHERE grant_date IS NOT NULL
ORDER BY grant_date DESC NULLS LAST

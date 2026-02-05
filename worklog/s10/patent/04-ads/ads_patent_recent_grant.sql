-- ============================================================
-- 模型: ads_patent_recent_grant
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 近 30 天内获得授权的专利列表，供仪表盘"最新授权"表格/滚动列表使用。
--
-- 直接从 DWD 层取数（不经过 DWS），因为：
--   - 这是明细列表，不是聚合统计
--   - 不需要预汇总
--   - 只需按授权日期过滤 + 排序
--
-- 字段说明：
--   - grant_date:      授权日期
--   - patent_no:       专利号
--   - patent_title_cn: 专利名称
--   - assignee_name:   申请人
--   - dept_name:       所属部门
--   - agent_org_name:  代理机构
--
-- 限制 200 条，避免数据量过大
-- ============================================================

{{ config(materialized='table', alias='ads_patent_recent_grant', schema='public', tags=['ads', 'patent']) }}

SELECT
  grant_date,
  patent_no,
  patent_title_cn,
  assignee_name,
  dept_name,
  agent_org_name

FROM {{ ref('dwd_patent') }}
WHERE grant_date >= (current_date - INTERVAL '30 day')::date
ORDER BY grant_date DESC NULLS LAST
LIMIT 200

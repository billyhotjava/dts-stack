-- ============================================================
-- 模型: ads_patent_detail_year
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 当年相关的专利明细清单，供仪表盘明细表格/导出使用。
--
-- 筛选口径：当年申请 OR 当年授权的专利（两个口径取并集）
-- 这样可以覆盖：
--   - 今年新申请的专利
--   - 往年申请但今年才授权的专利
--
-- 排序：按日期降序（优先展示最新的）
-- 限制 5000 条，避免数据量过大导致前端卡顿
--
-- 字段说明：
--   - application_date:  申请日期
--   - grant_date:        授权日期
--   - patent_no:         专利号
--   - patent_title_cn:   专利名称
--   - patent_type:       专利类型
--   - patent_status_std: 标准化状态
--   - patent_status_raw: 原始状态（便于用户对照）
--   - dept_name:         所属部门
--   - assignee_name:     申请人
-- ============================================================

{{ config(materialized='table', alias='ads_patent_detail_year', schema='public', tags=['ads', 'patent']) }}

SELECT
  application_date,
  grant_date,
  patent_no,
  patent_title_cn,
  patent_type,
  patent_status_std,
  patent_status_raw,
  dept_name,
  assignee_name

FROM {{ ref('dwd_patent') }}
WHERE application_year = EXTRACT(YEAR FROM current_date)::int
   OR grant_year = EXTRACT(YEAR FROM current_date)::int
ORDER BY COALESCE(application_date, grant_date) DESC NULLS LAST
LIMIT 5000

-- ============================================================
-- 模型: ads_patent_overdue_list
-- 层级: ADS (应用层)
-- 物化: table
-- ============================================================
--
-- 设计说明
-- --------
-- 超期未授权预警列表。
-- 筛选申请超过 365 天但仍未授权的专利，按超期天数降序排列。
--
-- 业务价值：
--   - 帮助科技管理部门识别审批进度异常的专利
--   - 超期 > 1 年且未授权，可能需要跟进催办或评估是否放弃
--   - 仪表盘可用红色标记高亮超期严重的记录
--
-- 筛选条件：
--   - application_date 非空
--   - is_granted = false（未授权）
--   - 申请距今超过 365 天
--
-- 字段说明：
--   - application_date:  申请日期
--   - patent_no:         专利号
--   - patent_title_cn:   专利名称
--   - dept_name:         所属部门
--   - dept_code:         部门编码
--   - patent_status_std: 当前标准化状态
--   - overdue_days:      超期天数（当前日期 - 申请日期）
--
-- 限制 2000 条
-- ============================================================

{{ config(materialized='table', alias='ads_patent_overdue_list', schema='public', tags=['ads', 'patent']) }}

SELECT
  application_date,
  patent_no,
  patent_title_cn,
  dept_name,
  dept_code,
  patent_status_std,
  (current_date - application_date)::int AS overdue_days

FROM {{ ref('dwd_patent') }}
WHERE application_date IS NOT NULL
  AND is_granted = false
  AND (current_date - application_date) > 365
ORDER BY overdue_days DESC
LIMIT 2000

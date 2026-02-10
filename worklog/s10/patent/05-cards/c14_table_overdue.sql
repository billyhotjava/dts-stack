-- Card C14: 受理超期预警
-- 组件类型: scroll-board
-- 映射: cols→header, rows→data
-- 筛选: 申请超过365天仍未授权
SELECT
  TO_CHAR(application_date, 'YYYY-MM-DD') AS "申请日期",
  patent_no                                AS "专利号",
  patent_title_cn                          AS "专利名称",
  overdue_days || '天'                     AS "超期天数"
FROM ads_patent_overdue_list
ORDER BY overdue_days DESC
LIMIT 200

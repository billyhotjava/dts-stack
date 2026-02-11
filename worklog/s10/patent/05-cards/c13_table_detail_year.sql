-- Card C13: 当年申请详情
-- 组件类型: scroll-board
-- 映射: cols→header, rows→data
SELECT
  TO_CHAR(application_date, 'YYYY-MM-DD') AS "申请日期",
  patent_no                                AS "专利号",
  patent_title_cn                          AS "专利名称",
  patent_type                              AS "类型",
  patent_status_std                        AS "状态"
FROM ads_patent_detail_year
WHERE application_year = EXTRACT(YEAR FROM current_date)
ORDER BY application_date DESC
LIMIT 200

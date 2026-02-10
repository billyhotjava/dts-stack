-- Card C12: 近期专利授权（近半年）
-- 组件类型: scroll-board
-- 映射: cols→header, rows→data (每列自动转字符串)
SELECT
  TO_CHAR(grant_date, 'YYYY-MM-DD') AS "授权日期",
  patent_no                          AS "专利号",
  patent_title_cn                    AS "专利名称",
  dept_name                          AS "部门"
FROM ads_patent_recent_grant
WHERE grant_date >= current_date - INTERVAL '180 days'
ORDER BY grant_date DESC
LIMIT 200

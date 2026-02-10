-- Card C08: 月度专利趋势
-- 组件类型: line-chart
-- 映射: col[0]=xAxisData(月份), col[1]=series[0](受理), col[2]=series[1](授权)
SELECT
  stat_month  AS "月份",
  accepted_cnt AS "受理",
  granted_cnt  AS "授权"
FROM ads_patent_month_trend
WHERE stat_year = EXTRACT(YEAR FROM current_date)
ORDER BY stat_month

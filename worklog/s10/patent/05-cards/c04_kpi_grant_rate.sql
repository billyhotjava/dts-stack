-- Card C04: 授权率
-- 组件类型: number-card
-- 映射: rows[0][0] → value
-- 注意: grant_rate 存储为小数(0.673), 乘100转为百分比展示
SELECT ROUND(this_year_grant_rate * 100, 1)
FROM ads_patent_dashboard_kpi
WHERE stat_year = EXTRACT(YEAR FROM current_date)

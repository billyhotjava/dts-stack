-- Card C02: 受理数量
-- 组件类型: number-card
-- 映射: rows[0][0] → value
SELECT this_year_accepted_cnt
FROM ads_patent_dashboard_kpi
WHERE stat_year = EXTRACT(YEAR FROM current_date)

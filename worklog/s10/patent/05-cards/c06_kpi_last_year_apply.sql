-- Card C06: 上年申请量
-- 组件类型: number-card
-- 映射: rows[0][0] → value
SELECT last_year_apply_cnt
FROM ads_patent_dashboard_kpi
WHERE stat_year = EXTRACT(YEAR FROM current_date)

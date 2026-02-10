-- Card C07: 专利类型占比
-- 组件类型: pie-chart
-- 映射: col[0]=name(patent_type), col[1]=value(apply_cnt)
SELECT patent_type, apply_cnt
FROM ads_patent_type_share
WHERE stat_year = EXTRACT(YEAR FROM current_date)
ORDER BY apply_cnt DESC

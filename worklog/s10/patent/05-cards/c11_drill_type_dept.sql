-- Card C11: 类型下各部门分布 (C07 下钻目标)
-- 组件类型: pie-chart (与父级相同)
-- 映射: col[0]=name(dept_name), col[1]=value(cnt)
-- 参数: {{patent_type}} — 由 C07 饼图点击传入
SELECT
  dept_name AS "部门",
  COUNT(*)  AS "数量"
FROM ads_patent_detail_year
WHERE patent_type = {{patent_type}}
  AND stat_year = EXTRACT(YEAR FROM current_date)
GROUP BY dept_name
ORDER BY COUNT(*) DESC
LIMIT 10

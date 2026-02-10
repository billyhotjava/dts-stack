-- Card C10: 部门专利类型分布 (C09 下钻目标)
-- 组件类型: bar-chart (与父级相同)
-- 映射: col[0]=xAxisData(patent_type), col[1]=series[0](cnt)
-- 参数: {{dept_name}} — 由 C09 柱状图点击传入
SELECT
  patent_type AS "专利类型",
  COUNT(*)    AS "数量"
FROM ads_patent_detail_year
WHERE dept_name = {{dept_name}}
  AND stat_year = EXTRACT(YEAR FROM current_date)
GROUP BY patent_type
ORDER BY COUNT(*) DESC

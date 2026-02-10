-- Card C09: 部门专利排行
-- 组件类型: bar-chart
-- 映射: col[0]=xAxisData(dept_name), col[1]=series[0](apply_cnt)
-- 下钻: 点击柱子 → C10, 参数 dept_name
SELECT
  dept_name  AS "部门",
  apply_cnt  AS "申请数"
FROM ads_patent_dept_rank
WHERE stat_year = EXTRACT(YEAR FROM current_date)
ORDER BY rank_no
LIMIT 10

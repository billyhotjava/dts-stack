-- ADS：未完成节点风险 KPI
-- 依赖：biz_dwd_project_node
DROP TABLE IF EXISTS public.biz_ads_project_incomplete_risk CASCADE;
CREATE TABLE public.biz_ads_project_incomplete_risk AS
SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,

  SUM(CASE WHEN d.risk_level = '高' AND d.is_incomplete THEN 1 ELSE 0 END)        AS incomplete_high_risk_cnt,
  SUM(CASE WHEN d.risk_level = '中' AND d.is_incomplete THEN 1 ELSE 0 END)        AS incomplete_mid_risk_cnt,
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END) AS incomplete_milestone_cnt,
  SUM(CASE WHEN d.node_type = '重大节点' AND d.is_incomplete THEN 1 ELSE 0 END)   AS incomplete_major_cnt,
  SUM(CASE WHEN d.node_type = '重要节点' AND d.is_incomplete THEN 1 ELSE 0 END)   AS incomplete_important_cnt

FROM public.biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month
ORDER BY d.plan_year, d.plan_month;

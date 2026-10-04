-- ADS：大项目概览（KPI 汇总）
-- 依赖：biz_dwd_project_node_enriched
DROP TABLE IF EXISTS public.biz_ads_major_project_overview CASCADE;
CREATE TABLE public.biz_ads_major_project_overview AS
SELECT
  program_id,
  program_name,
  major_project_id,
  major_project_name,
  COUNT(*) AS total_nodes,
  COUNT(DISTINCT subproject_id) AS subproject_count,
  SUM(CASE WHEN is_completed THEN 1 ELSE 0 END) AS completed_nodes,
  SUM(CASE WHEN node_status_bucket = 'overdue-open' THEN 1 ELSE 0 END) AS overdue_open_nodes,
  SUM(CASE WHEN risk_level = '高' THEN 1 ELSE 0 END) AS high_risk_nodes,
  SUM(CASE WHEN is_milestone THEN 1 ELSE 0 END) AS milestone_nodes,
  SUM(CASE WHEN is_milestone AND is_completed THEN 1 ELSE 0 END) AS milestone_completed_nodes,
  ROUND(AVG(health_score)::numeric, 2) AS avg_health_score,
  ROUND(AVG(GREATEST(COALESCE(delay_days, 0), 0))::numeric, 2) AS avg_delay_days,
  MAX(GREATEST(COALESCE(delay_days, 0), 0)) AS max_delay_days,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END AS completion_rate,
  CASE WHEN SUM(CASE WHEN is_milestone THEN 1 ELSE 0 END) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN is_milestone AND is_completed THEN 1 ELSE 0 END)::numeric
         / SUM(CASE WHEN is_milestone THEN 1 ELSE 0 END)::numeric, 4)
  END AS milestone_completion_rate
FROM public.biz_dwd_project_node_enriched
WHERE major_project_id IS NOT NULL
GROUP BY program_id, program_name, major_project_id, major_project_name;

-- DWS：周度子项目汇总
-- 依赖：biz_dwd_project_node_enriched
DROP TABLE IF EXISTS public.biz_dws_week_subproject_summary CASCADE;
CREATE TABLE public.biz_dws_week_subproject_summary AS
SELECT
  program_id,
  program_name,
  major_project_id,
  major_project_name,
  subproject_id,
  subproject_name,
  date_trunc('week', plan_date)::date AS week_start_date,
  to_char(date_trunc('week', plan_date), 'IYYY-"W"IW') AS plan_iso_week,
  COUNT(*) AS total_nodes,
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
WHERE subproject_id IS NOT NULL
  AND plan_date IS NOT NULL
GROUP BY
  program_id,
  program_name,
  major_project_id,
  major_project_name,
  subproject_id,
  subproject_name,
  date_trunc('week', plan_date);

{{ config(materialized='table', tags=['project-management', 'biz', 'project-cockpit', 'ads']) }}

SELECT
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
  ROUND(AVG(delay_days) FILTER (WHERE delay_days IS NOT NULL)::numeric, 2) AS avg_delay_days,
  MAX(delay_days) FILTER (WHERE delay_days IS NOT NULL) AS max_delay_days,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END AS completion_rate,
  CASE WHEN SUM(CASE WHEN is_milestone THEN 1 ELSE 0 END) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN is_milestone AND is_completed THEN 1 ELSE 0 END)::numeric
         / SUM(CASE WHEN is_milestone THEN 1 ELSE 0 END)::numeric, 4)
  END AS milestone_completion_rate
FROM {{ ref('biz_dwd_project_node_enriched') }}
WHERE major_project_id IS NOT NULL
GROUP BY major_project_id, major_project_name

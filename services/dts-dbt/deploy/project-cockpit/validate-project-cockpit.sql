-- Validate hierarchy coverage.
SELECT
  COUNT(*) AS total_nodes,
  COUNT(*) FILTER (WHERE major_project_id IS NOT NULL) AS major_bound_nodes,
  COUNT(*) FILTER (WHERE subproject_id IS NOT NULL) AS subproject_bound_nodes
FROM biz_dwd_project_node_enriched;

-- Validate overview totals and rank-ready metrics.
SELECT
  COUNT(*) AS major_projects,
  SUM(total_nodes) AS total_nodes,
  SUM(high_risk_nodes) AS total_high_risk_nodes,
  ROUND(AVG(avg_health_score)::numeric, 2) AS avg_health_score
FROM biz_ads_major_project_overview;

-- Validate tree snapshot level distribution.
SELECT
  snapshot_level,
  COUNT(*) AS row_count
FROM biz_ads_major_project_tree_snapshot
GROUP BY snapshot_level
ORDER BY snapshot_level;

-- Validate delay reason trend output.
SELECT
  delay_reason_category,
  SUM(delayed_node_count) AS delayed_nodes,
  SUM(high_risk_node_count) AS high_risk_nodes
FROM biz_ads_delay_reason_trend
GROUP BY delay_reason_category
ORDER BY delayed_nodes DESC, delay_reason_category;

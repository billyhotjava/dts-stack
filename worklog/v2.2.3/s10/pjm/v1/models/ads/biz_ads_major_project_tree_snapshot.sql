{{ config(materialized='table', tags=['project-management', 'biz', 'project-cockpit', 'ads']) }}

WITH major_level AS (
  SELECT
    'major'::text AS snapshot_level,
    major_project_id AS entity_id,
    NULL::text AS parent_id,
    major_project_name AS entity_name,
    major_project_id,
    major_project_name,
    NULL::text AS subproject_id,
    NULL::text AS subproject_name,
    COUNT(*) AS total_nodes,
    SUM(CASE WHEN is_completed THEN 1 ELSE 0 END) AS completed_nodes,
    SUM(CASE WHEN node_status_bucket = 'overdue-open' THEN 1 ELSE 0 END) AS overdue_open_nodes,
    SUM(CASE WHEN risk_level = '高' THEN 1 ELSE 0 END) AS high_risk_nodes,
    ROUND(AVG(health_score)::numeric, 2) AS avg_health_score,
    MIN(plan_date) AS plan_start_date,
    MAX(plan_date) AS plan_end_date,
    MAX(actual_date) AS actual_end_date,
    CASE WHEN COUNT(*) = 0 THEN 0
         ELSE ROUND(SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
    END AS progress_rate,
    0::int AS sort_order,
    NULL::text AS node_id,
    NULL::text AS node_task,
    NULL::text AS node_type,
    NULL::text AS risk_level,
    0::int AS delay_days
  FROM {{ ref('biz_dwd_project_node_enriched') }}
  WHERE major_project_id IS NOT NULL
  GROUP BY major_project_id, major_project_name
),
subproject_level AS (
  SELECT
    'subproject'::text AS snapshot_level,
    subproject_id AS entity_id,
    major_project_id AS parent_id,
    subproject_name AS entity_name,
    major_project_id,
    major_project_name,
    subproject_id,
    subproject_name,
    COUNT(*) AS total_nodes,
    SUM(CASE WHEN is_completed THEN 1 ELSE 0 END) AS completed_nodes,
    SUM(CASE WHEN node_status_bucket = 'overdue-open' THEN 1 ELSE 0 END) AS overdue_open_nodes,
    SUM(CASE WHEN risk_level = '高' THEN 1 ELSE 0 END) AS high_risk_nodes,
    ROUND(AVG(health_score)::numeric, 2) AS avg_health_score,
    MIN(plan_date) AS plan_start_date,
    MAX(plan_date) AS plan_end_date,
    MAX(actual_date) AS actual_end_date,
    CASE WHEN COUNT(*) = 0 THEN 0
         ELSE ROUND(SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
    END AS progress_rate,
    0::int AS sort_order,
    NULL::text AS node_id,
    NULL::text AS node_task,
    NULL::text AS node_type,
    NULL::text AS risk_level,
    0::int AS delay_days
  FROM {{ ref('biz_dwd_project_node_enriched') }}
  WHERE subproject_id IS NOT NULL
  GROUP BY
    major_project_id,
    major_project_name,
    subproject_id,
    subproject_name
),
node_level AS (
  SELECT
    'node'::text AS snapshot_level,
    node_id AS entity_id,
    subproject_id AS parent_id,
    node_task AS entity_name,
    major_project_id,
    major_project_name,
    subproject_id,
    subproject_name,
    1 AS total_nodes,
    CASE WHEN is_completed THEN 1 ELSE 0 END AS completed_nodes,
    CASE WHEN node_status_bucket = 'overdue-open' THEN 1 ELSE 0 END AS overdue_open_nodes,
    CASE WHEN risk_level = '高' THEN 1 ELSE 0 END AS high_risk_nodes,
    health_score::numeric(10, 2) AS avg_health_score,
    plan_date AS plan_start_date,
    plan_date AS plan_end_date,
    actual_date AS actual_end_date,
    CASE WHEN is_completed THEN 1 ELSE 0 END::numeric(10, 4) AS progress_rate,
    COALESCE(sort_order, 0) AS sort_order,
    node_id,
    node_task,
    node_type,
    risk_level,
    GREATEST(COALESCE(delay_days, 0), 0) AS delay_days
  FROM {{ ref('biz_dwd_project_node_enriched') }}
  WHERE subproject_id IS NOT NULL
)
SELECT * FROM major_level
UNION ALL
SELECT * FROM subproject_level
UNION ALL
SELECT * FROM node_level

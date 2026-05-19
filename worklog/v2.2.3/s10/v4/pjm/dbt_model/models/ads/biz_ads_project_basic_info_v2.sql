{{ config(materialized='table', tags=['project-management-v3', 'biz', 'ads', 'project-basic']) }}

-- 项目基础信息 ADS：为综合看板项目基本信息表提供稳定查询面。
-- 当前 ODS 未提供项目经费字段，project_budget_wan 保留为空，避免用节点数等指标冒充经费。

WITH project_rollup AS (
  SELECT
    d.project_no,
    MAX(NULLIF(btrim(d.project_manager), ''))  AS project_manager,
    MAX(NULLIF(btrim(d.institute_leader), '')) AS institute_leader,
    MAX(NULLIF(btrim(d.dept_leader), ''))      AS dept_leader,
    COUNT(*)                                   AS node_cnt,
    SUM(CASE WHEN d.is_completed  THEN 1 ELSE 0 END) AS completed_node_cnt,
    SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END) AS incomplete_node_cnt,
    MAX(d.last_update_time)                    AS last_update_time
  FROM {{ ref('biz_dwd_project_node_v2') }} d
  WHERE d.project_no IS NOT NULL
  GROUP BY d.project_no
)

SELECT
  r.project_no,
  r.project_no AS project_name,
  NULLIF(concat_ws(' / ', r.project_manager, r.institute_leader), '') AS project_leads,
  r.project_manager,
  r.institute_leader,
  r.dept_leader,
  NULL::numeric AS project_budget_wan,
  r.node_cnt,
  r.completed_node_cnt,
  r.incomplete_node_cnt,
  r.last_update_time,
  now() AS etl_time
FROM project_rollup r

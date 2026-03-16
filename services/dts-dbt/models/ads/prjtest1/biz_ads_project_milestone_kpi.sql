{{ config(materialized='table', tags=['project-management', 'biz', 'ads', 'kpi', 'admin-data-lake']) }}

{{
  config(
    materialized='table',
    tags=['project-management', 'biz', 'ads', 'kpi']
  )
}}

SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,

  -- 里程碑节点专项
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)                          AS milestone_on_time_cnt,
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END)                AS milestone_overdue_completed_cnt,
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.completion_status = '正常待完成' THEN 1 ELSE 0 END)     AS milestone_pending_cnt,
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END)                        AS milestone_incomplete_cnt,

  CASE
    WHEN (SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END)
        + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)
        + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END)) = 0
    THEN 0
    ELSE ROUND(
      (SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)
       + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END))::numeric
      / (SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END)
       + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)
       + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END))::numeric
    , 4)
  END AS milestone_completion_rate,

  -- 风险统计
  SUM(CASE WHEN d.risk_level = '高' THEN 1 ELSE 0 END)        AS high_risk_cnt,
  SUM(CASE WHEN d.risk_level = '中' THEN 1 ELSE 0 END)        AS mid_risk_cnt,

  -- 节点类型总数
  SUM(CASE WHEN d.node_type = '里程碑节点' THEN 1 ELSE 0 END) AS milestone_total_cnt,
  SUM(CASE WHEN d.node_type = '重大节点' THEN 1 ELSE 0 END)   AS major_total_cnt,
  SUM(CASE WHEN d.node_type = '重要节点' THEN 1 ELSE 0 END)   AS important_total_cnt

FROM {{ ref('biz_dwd_project_node') }} d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month
ORDER BY d.plan_year, d.plan_month

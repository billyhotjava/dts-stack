{{ config(materialized='table', tags=['project-management', 'biz', 'ads', 'kpi', 'dm-erp-demo']) }}

{{
  config(
    materialized='table',
    tags=['project-management', 'biz', 'ads', 'kpi']
  )
}}

WITH base AS (
  SELECT
    plan_year,
    plan_quarter,
    plan_month,

    SUM(CASE WHEN NOT is_general_node THEN abnormal_pending_cnt ELSE 0 END)             AS abnormal_pending_cnt,
    SUM(CASE WHEN NOT is_general_node THEN overdue_incomplete_unchanged_cnt ELSE 0 END) AS overdue_incomplete_unchanged_cnt,
    SUM(CASE WHEN NOT is_general_node THEN overdue_incomplete_changed_cnt ELSE 0 END)   AS overdue_incomplete_changed_cnt,
    SUM(CASE WHEN NOT is_general_node THEN overdue_completed_unchanged_cnt ELSE 0 END)  AS overdue_completed_unchanged_cnt,
    SUM(CASE WHEN NOT is_general_node THEN total_cnt - pending_normal_cnt ELSE 0 END)   AS due_cnt_non_general
  FROM {{ ref('biz_dws_period_node_type_summary') }}
  GROUP BY plan_year, plan_quarter, plan_month
)

SELECT
  plan_year,
  plan_quarter,
  plan_month,

  abnormal_pending_cnt,
  overdue_incomplete_unchanged_cnt,
  overdue_incomplete_changed_cnt,
  overdue_completed_unchanged_cnt,

  CASE WHEN due_cnt_non_general = 0 THEN 0
       ELSE ROUND(
         (abnormal_pending_cnt + overdue_incomplete_unchanged_cnt)::numeric
         / due_cnt_non_general::numeric, 4)
  END AS abnormal_rate,

  CASE WHEN due_cnt_non_general = 0 THEN 0
       ELSE ROUND(
         (overdue_incomplete_unchanged_cnt + overdue_incomplete_changed_cnt)::numeric
         / due_cnt_non_general::numeric, 4)
  END AS overdue_rate

FROM base
ORDER BY plan_year, plan_month

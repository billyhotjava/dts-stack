{{ config(materialized='table', tags=['project-management', 'biz', 'dws']) }}

SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,
  d.project_no,

  COUNT(*)                                                                        AS total_cnt,
  SUM(CASE WHEN d.completion_status = '正常待完成' THEN 1 ELSE 0 END)             AS pending_normal_cnt,
  SUM(CASE WHEN d.completion_status != '正常待完成' OR d.is_due THEN 1 ELSE 0 END) AS due_cnt,
  SUM(CASE WHEN d.is_on_time THEN 1 ELSE 0 END)                                  AS on_time_cnt,
  SUM(CASE WHEN d.is_overdue_completed THEN 1 ELSE 0 END)                        AS overdue_completed_cnt,
  SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)                                AS completed_cnt,
  SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END)                               AS incomplete_cnt,
  SUM(CASE WHEN d.completion_status = '不正常待变更' THEN 1 ELSE 0 END)           AS abnormal_pending_cnt,
  SUM(CASE WHEN d.completion_status = '超期未完成未变更' THEN 1 ELSE 0 END)       AS overdue_incomplete_unchanged_cnt,
  SUM(CASE WHEN d.completion_status = '超期未完成已变更' THEN 1 ELSE 0 END)       AS overdue_incomplete_changed_cnt,
  SUM(CASE WHEN d.completion_status = '超期已完成未变更' THEN 1 ELSE 0 END)       AS overdue_completed_unchanged_cnt

FROM {{ ref('biz_dwd_project_node') }} d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month, d.project_no

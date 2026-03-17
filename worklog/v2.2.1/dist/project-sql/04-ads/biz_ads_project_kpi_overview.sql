-- ADS：项目 KPI 总览（含一般节点）
-- 依赖：biz_dws_period_node_summary, biz_dwd_project_node
DROP TABLE IF EXISTS public.biz_ads_project_kpi_overview CASCADE;
CREATE TABLE public.biz_ads_project_kpi_overview AS
WITH base AS (
  SELECT
    plan_year,
    plan_quarter,
    plan_month,

    SUM(total_cnt)                          AS total_cnt,
    SUM(pending_normal_cnt)                 AS pending_normal_cnt,
    SUM(due_cnt)                            AS due_cnt,
    SUM(on_time_cnt)                        AS on_time_cnt,
    SUM(overdue_completed_cnt)              AS overdue_completed_cnt,
    SUM(completed_cnt)                      AS completed_cnt,
    SUM(incomplete_cnt)                     AS incomplete_cnt,
    SUM(abnormal_pending_cnt)               AS abnormal_pending_cnt,
    SUM(overdue_incomplete_unchanged_cnt)   AS overdue_incomplete_unchanged_cnt,
    SUM(overdue_incomplete_changed_cnt)     AS overdue_incomplete_changed_cnt
  FROM public.biz_dws_period_node_summary
  GROUP BY plan_year, plan_quarter, plan_month
),

outside_completed AS (
  SELECT
    d.actual_year   AS plan_year,
    d.actual_month  AS plan_month,
    COUNT(*)        AS outside_completed_cnt
  FROM public.biz_dwd_project_node d
  WHERE d.is_completed = true
    AND d.actual_year IS NOT NULL
    AND (d.plan_year != d.actual_year OR d.plan_month != d.actual_month)
  GROUP BY d.actual_year, d.actual_month
)

SELECT
  b.plan_year,
  b.plan_quarter,
  b.plan_month,

  b.total_cnt,
  b.pending_normal_cnt,
  b.due_cnt,
  COALESCE(oc.outside_completed_cnt, 0)                           AS outside_completed_cnt,
  b.incomplete_cnt,
  b.on_time_cnt,
  b.overdue_completed_cnt,
  b.completed_cnt + COALESCE(oc.outside_completed_cnt, 0)         AS completed_total_cnt,

  CASE WHEN (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0)) = 0 THEN 0
       ELSE ROUND(
         (b.completed_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric
         / (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric, 4)
  END AS completion_rate,

  CASE WHEN (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0)) = 0 THEN 0
       ELSE ROUND(
         b.on_time_cnt::numeric
         / (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric, 4)
  END AS on_time_rate,

  CASE WHEN (b.total_cnt + COALESCE(oc.outside_completed_cnt, 0)) = 0 THEN 0
       ELSE ROUND(
         (b.overdue_completed_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric
         / (b.total_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric, 4)
  END AS overdue_completion_rate,

  b.abnormal_pending_cnt,
  b.overdue_incomplete_unchanged_cnt,
  b.overdue_incomplete_changed_cnt

FROM base b
LEFT JOIN outside_completed oc
  ON oc.plan_year = b.plan_year AND oc.plan_month = b.plan_month
ORDER BY b.plan_year, b.plan_month;

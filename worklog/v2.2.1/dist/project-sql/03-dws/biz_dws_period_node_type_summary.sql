-- DWS：期间节点类型汇总（按年/季/月/项目/节点类型维度）
-- 依赖：biz_dwd_project_node
DROP TABLE IF EXISTS public.biz_dws_period_node_type_summary CASCADE;
CREATE TABLE public.biz_dws_period_node_type_summary AS
SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,
  d.project_no,
  d.node_type,
  d.is_general_node,

  COUNT(*)                                                                        AS total_cnt,
  SUM(CASE WHEN d.is_on_time THEN 1 ELSE 0 END)                                  AS on_time_cnt,
  SUM(CASE WHEN d.is_overdue_completed THEN 1 ELSE 0 END)                        AS overdue_completed_cnt,
  SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)                                AS completed_cnt,
  SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END)                               AS incomplete_cnt,
  SUM(CASE WHEN d.completion_status = '正常待完成' THEN 1 ELSE 0 END)             AS pending_normal_cnt,
  SUM(CASE WHEN d.completion_status = '不正常待变更' THEN 1 ELSE 0 END)           AS abnormal_pending_cnt,
  SUM(CASE WHEN d.completion_status = '超期未完成未变更' THEN 1 ELSE 0 END)       AS overdue_incomplete_unchanged_cnt,
  SUM(CASE WHEN d.completion_status = '超期未完成已变更' THEN 1 ELSE 0 END)       AS overdue_incomplete_changed_cnt,
  SUM(CASE WHEN d.completion_status = '超期已完成未变更' THEN 1 ELSE 0 END)       AS overdue_completed_unchanged_cnt,
  SUM(CASE WHEN d.completion_status = '超期已完成已变更' THEN 1 ELSE 0 END)       AS overdue_completed_changed_cnt

FROM public.biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month, d.project_no, d.node_type, d.is_general_node;

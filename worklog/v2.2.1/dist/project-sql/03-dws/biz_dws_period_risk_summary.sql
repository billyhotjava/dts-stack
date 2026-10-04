-- DWS：期间风险汇总（按年/季/月/项目/风险等级/节点类型维度）
-- 依赖：biz_dwd_project_node
DROP TABLE IF EXISTS public.biz_dws_period_risk_summary CASCADE;
CREATE TABLE public.biz_dws_period_risk_summary AS
SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,
  d.project_no,
  d.risk_level,
  d.node_type,

  COUNT(*)                                           AS total_cnt,
  SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END)  AS incomplete_cnt,
  SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)   AS completed_cnt

FROM public.biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
  AND d.risk_level IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month, d.project_no, d.risk_level, d.node_type;

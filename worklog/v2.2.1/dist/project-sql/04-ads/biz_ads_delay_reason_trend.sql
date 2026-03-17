-- ADS：延期原因趋势（按大项目/周/原因分类）
-- 依赖：biz_dwd_project_node_enriched
DROP TABLE IF EXISTS public.biz_ads_delay_reason_trend CASCADE;
CREATE TABLE public.biz_ads_delay_reason_trend AS
SELECT
  program_id,
  program_name,
  major_project_id,
  major_project_name,
  dept,
  date_trunc('week', plan_date)::date AS week_start_date,
  to_char(date_trunc('week', plan_date), 'IYYY-"W"IW') AS plan_iso_week,
  delay_reason_category,
  delay_reason_label,
  COUNT(*) FILTER (
    WHERE node_status_bucket = 'overdue-open'
       OR completion_status IN ('超期已完成已变更', '超期已完成未变更')
  ) AS delayed_node_count,
  COUNT(*) FILTER (WHERE risk_level = '高') AS high_risk_node_count,
  COUNT(DISTINCT subproject_id) FILTER (
    WHERE node_status_bucket = 'overdue-open'
       OR completion_status IN ('超期已完成已变更', '超期已完成未变更')
  ) AS delayed_subproject_count
FROM public.biz_dwd_project_node_enriched
WHERE plan_date IS NOT NULL
GROUP BY
  program_id,
  program_name,
  major_project_id,
  major_project_name,
  dept,
  date_trunc('week', plan_date),
  delay_reason_category,
  delay_reason_label;

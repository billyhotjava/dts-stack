-- DWD：项目节点富化表（含大项目、子项目、节点映射、延期分类等宽表）
-- 依赖：biz_dwd_project_node, pm_map_node_subject, pm_dim_subproject,
--       pm_dim_major_project, pm_dim_delay_reason
DROP TABLE IF EXISTS public.biz_dwd_project_node_enriched CASCADE;
CREATE TABLE public.biz_dwd_project_node_enriched AS
WITH base AS (
  SELECT *
  FROM public.biz_dwd_project_node
),
classified AS (
  SELECT
    b.*,
    CASE
      WHEN NULLIF(btrim(COALESCE(b.incomplete_reason, '')), '') IS NULL THEN 'normal'
      WHEN b.incomplete_reason ~ '(算法|技术|仿真|硬件|精度)' THEN 'technical'
      WHEN b.incomplete_reason ~ '(质量|可靠性|返工|故障)' THEN 'quality'
      WHEN b.incomplete_reason ~ '(变更|延误|重新定义)' THEN 'change'
      WHEN b.incomplete_reason ~ '(联调|接口|协同)' THEN 'coordination'
      WHEN b.incomplete_reason ~ '(供货|到货|加工|器件)' THEN 'supplier'
      WHEN b.incomplete_reason ~ '(测试|标定|复测|暗室)' THEN 'test'
      WHEN b.incomplete_reason ~ '(归档|周报|报告)' THEN 'archive'
      ELSE 'normal'
    END AS delay_reason_category_fallback
  FROM base b
)
SELECT
  c.*,
  mp.major_project_id,
  mp.major_project_code,
  mp.major_project_name,
  mp.program_id,
  mp.program_name,
  mp.project_level AS major_project_level,
  mp.owner_dept AS major_project_owner_dept,
  mp.owner_leader AS major_project_owner_leader,
  mp.priority_level AS major_project_priority_level,
  sp.subproject_id,
  sp.subproject_code,
  sp.subproject_name,
  sp.owner_dept AS subproject_owner_dept,
  sp.owner_user AS subproject_owner_user,
  sp.project_manager AS subproject_owner_manager,
  sp.priority_level AS subproject_priority_level,
  map.map_id,
  map.node_category,
  map.is_key_node,
  map.is_milestone,
  map.sort_order,
  COALESCE(map.delay_reason_category, c.delay_reason_category_fallback) AS delay_reason_category,
  dr.delay_reason_label,
  CASE
    WHEN c.is_completed AND c.is_on_time THEN 'closed-on-time'
    WHEN c.is_completed AND c.is_overdue_completed THEN 'closed-delayed'
    WHEN c.is_incomplete AND COALESCE(c.delay_days, 0) > 0 THEN 'overdue-open'
    WHEN c.is_incomplete THEN 'risk-open'
    WHEN c.is_due THEN 'in-flight'
    ELSE 'planned'
  END AS node_status_bucket,
  CASE COALESCE(c.risk_level, '')
    WHEN '高' THEN 3
    WHEN '中' THEN 2
    WHEN '低' THEN 1
    ELSE 0
  END AS risk_rank,
  GREATEST(COALESCE(c.delay_days, 0), 0) AS overdue_days,
  CASE
    WHEN c.plan_date IS NOT NULL THEN (c.plan_date - current_date)::int
    ELSE NULL
  END AS days_to_plan,
  LEAST(
    100,
    GREATEST(
      0,
      100
      - CASE COALESCE(c.risk_level, '')
          WHEN '高' THEN 35
          WHEN '中' THEN 18
          WHEN '低' THEN 5
          ELSE 0
        END
      - LEAST(GREATEST(COALESCE(c.delay_days, 0), 0), 30)
      - CASE
          WHEN c.is_incomplete THEN 12
          WHEN c.completion_status = '正常待完成' THEN 4
          ELSE 0
        END
      + CASE
          WHEN c.is_completed AND c.is_on_time THEN 8
          WHEN c.is_completed THEN 3
          ELSE 0
        END
    )
  ) AS health_score
FROM classified c
LEFT JOIN public.pm_map_node_subject map
  ON map.project_no = c.project_no
 AND map.subsystem = c.subsystem
 AND map.node_task = c.node_task
LEFT JOIN public.pm_dim_subproject sp
  ON sp.subproject_id = map.subproject_id
LEFT JOIN public.pm_dim_major_project mp
  ON mp.major_project_id = COALESCE(map.major_project_id, sp.major_project_id)
LEFT JOIN public.pm_dim_delay_reason dr
  ON dr.delay_reason_category = COALESCE(map.delay_reason_category, c.delay_reason_category_fallback);

CREATE INDEX IF NOT EXISTS idx_biz_dwd_enriched_major ON public.biz_dwd_project_node_enriched(major_project_id);
CREATE INDEX IF NOT EXISTS idx_biz_dwd_enriched_sub   ON public.biz_dwd_project_node_enriched(subproject_id);
CREATE INDEX IF NOT EXISTS idx_biz_dwd_enriched_month ON public.biz_dwd_project_node_enriched(plan_month);

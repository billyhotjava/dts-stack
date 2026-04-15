{{ config(materialized='table', tags=['project-management-v3', 'biz', 'ads', 'derived']) }}

-- 进度域二次指标：对齐二次指标大表 #1-6
-- 依赖: biz_ads_progress_kpi_v2

SELECT
  k.plan_year,
  k.plan_month,

  -- #1 pjm_prog_on_time_rate: 节点按时完成率 (预警 <80%)
  CASE WHEN k.due_cnt = 0 THEN 0
       ELSE ROUND(k.on_time_cnt::numeric / k.due_cnt::numeric * 100, 2)
  END AS pjm_prog_on_time_rate,

  -- #2 pjm_prog_overdue_rate: 节点超期率 (预警 >15%)
  CASE WHEN k.due_cnt = 0 THEN 0
       ELSE ROUND((k.overdue_incomplete_unchanged_non_general_cnt
                  + k.overdue_incomplete_changed_non_general_cnt)::numeric
                / k.due_cnt::numeric * 100, 2)
  END AS pjm_prog_overdue_rate,

  -- #3 pjm_prog_abnormal_rate: 节点不正常率 (预警 >20%)
  CASE WHEN k.due_cnt = 0 THEN 0
       ELSE ROUND((k.abnormal_pending_non_general_cnt
                  + k.overdue_incomplete_unchanged_non_general_cnt)::numeric
                / k.due_cnt::numeric * 100, 2)
  END AS pjm_prog_abnormal_rate,

  -- #4 pjm_prog_milestone_on_time_rate: 里程碑按时完成率 (预警 <90%)
  CASE WHEN k.milestone_total_cnt = 0 THEN 0
       ELSE ROUND(k.milestone_on_time_cnt::numeric
                / k.milestone_total_cnt::numeric * 100, 2)
  END AS pjm_prog_milestone_on_time_rate,

  -- #5 pjm_prog_high_risk_incomplete_ratio: 高风险未完成占比 (预警 >30%)
  CASE WHEN k.incomplete_cnt = 0 THEN 0
       ELSE ROUND(k.incomplete_high_risk_cnt::numeric
                / k.incomplete_cnt::numeric * 100, 2)
  END AS pjm_prog_high_risk_incomplete_ratio,

  -- #6 pjm_prog_health_score: 进度健康度 (预警 <70)
  -- 公式: 按时完成率×0.4 + 里程碑按时率×0.3 + (100-不正常率)×0.3
  ROUND(
    (CASE WHEN k.due_cnt = 0 THEN 0
          ELSE k.on_time_cnt::numeric / k.due_cnt::numeric * 100
     END) * 0.4
    +
    (CASE WHEN k.milestone_total_cnt = 0 THEN 0
          ELSE k.milestone_on_time_cnt::numeric / k.milestone_total_cnt::numeric * 100
     END) * 0.3
    +
    (100 - CASE WHEN k.due_cnt = 0 THEN 0
                ELSE (k.abnormal_pending_non_general_cnt
                    + k.overdue_incomplete_unchanged_non_general_cnt)::numeric
                   / k.due_cnt::numeric * 100
           END) * 0.3
  , 2) AS pjm_prog_health_score,

  -- 预警标志
  CASE WHEN k.due_cnt > 0
        AND k.on_time_cnt::numeric / k.due_cnt::numeric * 100 < 80
       THEN true ELSE false
  END AS warn_on_time_rate,

  CASE WHEN k.due_cnt > 0
        AND (k.overdue_incomplete_unchanged_non_general_cnt
           + k.overdue_incomplete_changed_non_general_cnt)::numeric
           / k.due_cnt::numeric * 100 > 15
       THEN true ELSE false
  END AS warn_overdue_rate,

  CASE WHEN k.milestone_total_cnt > 0
        AND k.milestone_on_time_cnt::numeric / k.milestone_total_cnt::numeric * 100 < 90
       THEN true ELSE false
  END AS warn_milestone_rate

FROM {{ ref('biz_ads_progress_kpi_v2') }} k
ORDER BY k.plan_year, k.plan_month

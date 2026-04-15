{{ config(materialized='table', tags=['project-management-v2', 'biz', 'ads', 'kpi']) }}

-- 进度域原始指标 ADS：对齐原始指标大表 #1-33
-- 粒度: plan_month（跨项目聚合）
-- 存储分子/分母，支持跨月 SUM 重算

SELECT
  s.plan_year,
  s.plan_month,

  -- #1-7 周期内基础计数
  SUM(s.total_cnt)                           AS total_cnt,
  SUM(s.pending_normal_cnt)                  AS pending_normal_cnt,
  SUM(s.due_cnt)                             AS due_cnt,
  SUM(s.outside_completed_cnt)               AS outside_completed_cnt,
  SUM(s.incomplete_cnt)                      AS incomplete_cnt,
  SUM(s.on_time_cnt)                         AS on_time_cnt,
  SUM(s.overdue_completed_cnt)               AS overdue_completed_cnt,
  SUM(s.overdue_completed_unchanged_cnt)     AS overdue_completed_unchanged_cnt,

  -- #8 完成总数
  SUM(s.completed_total_cnt)                 AS completed_total_cnt,

  -- #9 完成百分比（分子/分母形式）
  CASE WHEN (SUM(s.due_cnt) + SUM(s.outside_completed_cnt)) = 0 THEN 0
       ELSE ROUND(SUM(s.completed_total_cnt)::numeric
                / (SUM(s.due_cnt) + SUM(s.outside_completed_cnt))::numeric, 4)
  END AS completion_rate,

  -- #10 按时完成百分比
  CASE WHEN (SUM(s.due_cnt) + SUM(s.outside_completed_cnt)) = 0 THEN 0
       ELSE ROUND(SUM(s.on_time_cnt)::numeric
                / (SUM(s.due_cnt) + SUM(s.outside_completed_cnt))::numeric, 4)
  END AS on_time_rate,

  -- #11 超期完成百分比
  CASE WHEN (SUM(s.total_cnt) + SUM(s.outside_completed_cnt)) = 0 THEN 0
       ELSE ROUND((SUM(s.overdue_completed_cnt) + SUM(s.outside_completed_cnt))::numeric
                / (SUM(s.total_cnt) + SUM(s.outside_completed_cnt))::numeric, 4)
  END AS overdue_completion_rate,

  -- #12-15 排除一般节点
  SUM(s.abnormal_pending_non_general_cnt)                AS abnormal_pending_non_general_cnt,
  SUM(s.overdue_incomplete_unchanged_non_general_cnt)    AS overdue_incomplete_unchanged_non_general_cnt,
  SUM(s.overdue_incomplete_changed_non_general_cnt)      AS overdue_incomplete_changed_non_general_cnt,
  SUM(s.overdue_completed_unchanged_non_general_cnt)     AS overdue_completed_unchanged_non_general_cnt,

  -- #16 异常率分子（不正常待变更 + 超期未完成未变更）
  SUM(s.abnormal_pending_non_general_cnt)
    + SUM(s.overdue_incomplete_unchanged_non_general_cnt) AS abnormal_rate_numerator,
  -- #17 超期率分子（超期未完成未变更 + 超期未完成已变更）
  SUM(s.overdue_incomplete_unchanged_non_general_cnt)
    + SUM(s.overdue_incomplete_changed_non_general_cnt)   AS overdue_rate_numerator,

  -- #16-17 比率（分母 = due_cnt）
  CASE WHEN SUM(s.due_cnt) = 0 THEN 0
       ELSE ROUND((SUM(s.abnormal_pending_non_general_cnt)
                  + SUM(s.overdue_incomplete_unchanged_non_general_cnt))::numeric
                / SUM(s.due_cnt)::numeric, 4)
  END AS abnormal_rate,

  CASE WHEN SUM(s.due_cnt) = 0 THEN 0
       ELSE ROUND((SUM(s.overdue_incomplete_unchanged_non_general_cnt)
                  + SUM(s.overdue_incomplete_changed_non_general_cnt))::numeric
                / SUM(s.due_cnt)::numeric, 4)
  END AS overdue_rate,

  -- #18-22 未完成风险/类型分类
  SUM(s.incomplete_high_risk_cnt)            AS incomplete_high_risk_cnt,
  SUM(s.incomplete_mid_risk_cnt)             AS incomplete_mid_risk_cnt,
  SUM(s.incomplete_milestone_cnt)            AS incomplete_milestone_cnt,
  SUM(s.incomplete_major_cnt)                AS incomplete_major_cnt,
  SUM(s.incomplete_important_cnt)            AS incomplete_important_cnt,

  -- #23-25 里程碑完成情况
  SUM(s.milestone_on_time_cnt)               AS milestone_on_time_cnt,
  SUM(s.milestone_overdue_completed_cnt)     AS milestone_overdue_completed_cnt,
  SUM(s.milestone_pending_cnt)               AS milestone_pending_cnt,

  -- #26 里程碑完成总百分比
  CASE WHEN (SUM(s.incomplete_milestone_cnt) + SUM(s.milestone_on_time_cnt)
           + SUM(s.milestone_overdue_completed_cnt)) = 0 THEN 0
       ELSE ROUND((SUM(s.milestone_on_time_cnt) + SUM(s.milestone_overdue_completed_cnt))::numeric
                / (SUM(s.incomplete_milestone_cnt) + SUM(s.milestone_on_time_cnt)
                 + SUM(s.milestone_overdue_completed_cnt))::numeric, 4)
  END AS milestone_completion_rate,

  -- #27-28 风险节点
  SUM(s.high_risk_cnt)                       AS high_risk_cnt,
  SUM(s.mid_risk_cnt)                        AS mid_risk_cnt,

  -- #29-31 节点类型总数
  SUM(s.milestone_total_cnt)                 AS milestone_total_cnt,
  SUM(s.major_total_cnt)                     AS major_total_cnt,
  SUM(s.important_total_cnt)                 AS important_total_cnt,

  -- #32 里程碑按时完成率
  CASE WHEN SUM(s.milestone_total_cnt) = 0 THEN 0
       ELSE ROUND(SUM(s.milestone_on_time_cnt)::numeric
                / SUM(s.milestone_total_cnt)::numeric, 4)
  END AS milestone_on_time_rate,

  -- #33 里程碑超期完成率
  CASE WHEN SUM(s.milestone_total_cnt) = 0 THEN 0
       ELSE ROUND(SUM(s.milestone_overdue_completed_cnt)::numeric
                / SUM(s.milestone_total_cnt)::numeric, 4)
  END AS milestone_overdue_rate

FROM {{ ref('biz_dws_progress_monthly_v2') }} s
GROUP BY s.plan_year, s.plan_month
ORDER BY s.plan_year, s.plan_month

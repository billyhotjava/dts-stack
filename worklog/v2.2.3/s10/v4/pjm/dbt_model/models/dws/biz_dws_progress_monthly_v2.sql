{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dws']) }}

-- 进度域月度汇总：对齐原始指标大表 #1-33
-- 粒度: project_no × plan_month
-- 存储分子/分母原始值，支持跨月 SUM(分子)/SUM(分母) 重算比率

WITH base AS (
  SELECT * FROM {{ ref('biz_dwd_project_node_v2') }}
  WHERE plan_month IS NOT NULL
),

-- 周期内节点统计 (#1-11)
period_stats AS (
  SELECT
    plan_year,
    plan_quarter,
    plan_month,
    project_no,

    -- #1 项目本周期节点总数
    COUNT(*)                                                                         AS total_cnt,
    -- #2 正常待完成
    SUM(CASE WHEN is_pending_normal THEN 1 ELSE 0 END)                               AS pending_normal_cnt,
    -- #3 已到时间节点总数 = 总数 - 正常待完成
    COUNT(*) - SUM(CASE WHEN is_pending_normal THEN 1 ELSE 0 END)                    AS due_cnt,
    -- #5 未完成总数
    SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END)                                   AS incomplete_cnt,
    -- #6 按时完成数
    SUM(CASE WHEN is_on_time THEN 1 ELSE 0 END)                                      AS on_time_cnt,
    -- #7 超期完成数
    SUM(CASE WHEN is_overdue_completed THEN 1 ELSE 0 END)                            AS overdue_completed_cnt,
    -- 超期已完成未变更
    SUM(CASE WHEN is_overdue_done_unchanged THEN 1 ELSE 0 END)                       AS overdue_completed_unchanged_cnt,

    -- #12 不正常待变更（排除一般节点）
    SUM(CASE WHEN is_abnormal_pending AND NOT is_general_node
             THEN 1 ELSE 0 END)                                                      AS abnormal_pending_non_general_cnt,
    -- #13 超期未完成未变更（排除一般节点）
    SUM(CASE WHEN is_overdue_unchanged AND NOT is_general_node
             THEN 1 ELSE 0 END)                                                      AS overdue_incomplete_unchanged_non_general_cnt,
    -- #14 超期未完成已变更（排除一般节点）
    SUM(CASE WHEN is_overdue_changed AND NOT is_general_node
             THEN 1 ELSE 0 END)                                                      AS overdue_incomplete_changed_non_general_cnt,
    -- #15 超期已完成未变更（排除一般节点）
    SUM(CASE WHEN is_overdue_done_unchanged AND NOT is_general_node
             THEN 1 ELSE 0 END)                                                      AS overdue_completed_unchanged_non_general_cnt,

    -- #18-22 未完成节点按风险/类型分类（字典派生布尔）
    SUM(CASE WHEN is_incomplete AND is_high_risk      THEN 1 ELSE 0 END)             AS incomplete_high_risk_cnt,
    SUM(CASE WHEN is_incomplete AND is_mid_risk       THEN 1 ELSE 0 END)             AS incomplete_mid_risk_cnt,
    SUM(CASE WHEN is_incomplete AND is_milestone_node THEN 1 ELSE 0 END)             AS incomplete_milestone_cnt,
    SUM(CASE WHEN is_incomplete AND is_major_node     THEN 1 ELSE 0 END)             AS incomplete_major_cnt,
    SUM(CASE WHEN is_incomplete AND is_important_node THEN 1 ELSE 0 END)             AS incomplete_important_cnt,

    -- #23-25 里程碑节点完成情况
    SUM(CASE WHEN is_milestone_node AND is_on_time THEN 1 ELSE 0 END)                AS milestone_on_time_cnt,
    SUM(CASE WHEN is_milestone_node AND is_overdue_completed THEN 1 ELSE 0 END)      AS milestone_overdue_completed_cnt,
    SUM(CASE WHEN is_milestone_node AND is_pending_normal THEN 1 ELSE 0 END)         AS milestone_pending_cnt,

    -- 业务 4 大分类（gpmc-overview-v4 口径：不正常待变更合并入"超期未完成"）
    SUM(CASE WHEN is_on_time                      THEN 1 ELSE 0 END)                 AS on_time_cnt_v2,
    SUM(CASE WHEN is_pending_normal               THEN 1 ELSE 0 END)                 AS pending_normal_cnt_v2,
    SUM(CASE WHEN is_overdue_completed_effective  THEN 1 ELSE 0 END)                 AS overdue_completed_effective_cnt,
    SUM(CASE WHEN is_overdue_incomplete_effective THEN 1 ELSE 0 END)                 AS overdue_incomplete_effective_cnt,

    -- #27-28 风险节点数（全量）
    SUM(CASE WHEN is_high_risk THEN 1 ELSE 0 END)                                    AS high_risk_cnt,
    SUM(CASE WHEN is_mid_risk  THEN 1 ELSE 0 END)                                    AS mid_risk_cnt,

    -- #29-31 各类型节点总数
    SUM(CASE WHEN is_milestone_node THEN 1 ELSE 0 END)                               AS milestone_total_cnt,
    SUM(CASE WHEN is_major_node     THEN 1 ELSE 0 END)                               AS major_total_cnt,
    SUM(CASE WHEN is_important_node THEN 1 ELSE 0 END)                               AS important_total_cnt

  FROM base
  GROUP BY plan_year, plan_quarter, plan_month, project_no
),

-- #4 周期以外完成节点（actual_month 在本月但 plan_month 不在本月）
outside_completed AS (
  SELECT
    actual_year  AS plan_year,
    actual_month AS plan_month,
    project_no,
    COUNT(*)     AS outside_completed_cnt
  FROM base
  WHERE is_completed = true
    AND actual_month IS NOT NULL
    AND (plan_year != actual_year OR plan_month != actual_month)
  GROUP BY actual_year, actual_month, project_no
)

SELECT
  COALESCE(p.plan_year, oc.plan_year) AS plan_year,
  COALESCE(p.plan_quarter, EXTRACT(QUARTER FROM to_date(oc.plan_month || '-01', 'YYYY-MM-DD'))::int) AS plan_quarter,
  COALESCE(p.plan_month, oc.plan_month) AS plan_month,
  COALESCE(p.project_no, oc.project_no) AS project_no,

  -- 周期内指标 (#1-7)
  COALESCE(p.total_cnt, 0) AS total_cnt,
  COALESCE(p.pending_normal_cnt, 0) AS pending_normal_cnt,
  COALESCE(p.due_cnt, 0) AS due_cnt,
  COALESCE(oc.outside_completed_cnt, 0)                                    AS outside_completed_cnt,
  COALESCE(p.incomplete_cnt, 0) AS incomplete_cnt,
  COALESCE(p.on_time_cnt, 0) AS on_time_cnt,
  COALESCE(p.overdue_completed_cnt, 0) AS overdue_completed_cnt,
  COALESCE(p.overdue_completed_unchanged_cnt, 0) AS overdue_completed_unchanged_cnt,

  -- #8 完成总数（含周期外）
  (COALESCE(p.on_time_cnt, 0) + COALESCE(p.overdue_completed_cnt, 0) + COALESCE(oc.outside_completed_cnt, 0)) AS completed_total_cnt,

  -- 排除一般节点 (#12-15)
  COALESCE(p.abnormal_pending_non_general_cnt, 0) AS abnormal_pending_non_general_cnt,
  COALESCE(p.overdue_incomplete_unchanged_non_general_cnt, 0) AS overdue_incomplete_unchanged_non_general_cnt,
  COALESCE(p.overdue_incomplete_changed_non_general_cnt, 0) AS overdue_incomplete_changed_non_general_cnt,
  COALESCE(p.overdue_completed_unchanged_non_general_cnt, 0) AS overdue_completed_unchanged_non_general_cnt,

  -- 未完成风险/类型分类 (#18-22)
  COALESCE(p.incomplete_high_risk_cnt, 0) AS incomplete_high_risk_cnt,
  COALESCE(p.incomplete_mid_risk_cnt, 0) AS incomplete_mid_risk_cnt,
  COALESCE(p.incomplete_milestone_cnt, 0) AS incomplete_milestone_cnt,
  COALESCE(p.incomplete_major_cnt, 0) AS incomplete_major_cnt,
  COALESCE(p.incomplete_important_cnt, 0) AS incomplete_important_cnt,

  -- 里程碑统计 (#23-25)
  COALESCE(p.milestone_on_time_cnt, 0) AS milestone_on_time_cnt,
  COALESCE(p.milestone_overdue_completed_cnt, 0) AS milestone_overdue_completed_cnt,
  COALESCE(p.milestone_pending_cnt, 0) AS milestone_pending_cnt,

  -- 风险节点 (#27-28)
  COALESCE(p.high_risk_cnt, 0) AS high_risk_cnt,
  COALESCE(p.mid_risk_cnt, 0) AS mid_risk_cnt,

  -- 节点类型总数 (#29-31)
  COALESCE(p.milestone_total_cnt, 0) AS milestone_total_cnt,
  COALESCE(p.major_total_cnt, 0) AS major_total_cnt,
  COALESCE(p.important_total_cnt, 0) AS important_total_cnt,

  -- 业务 4 大分类（对齐 gpmc-overview-v4 口径）
  COALESCE(p.on_time_cnt_v2, 0) AS on_time_cnt_v2,
  COALESCE(p.pending_normal_cnt_v2, 0) AS pending_normal_cnt_v2,
  COALESCE(p.overdue_completed_effective_cnt, 0) AS overdue_completed_effective_cnt,
  COALESCE(p.overdue_incomplete_effective_cnt, 0) AS overdue_incomplete_effective_cnt

-- 保留仅有周期外完成记录的项目月份；该月份的计划类计数为 0。
FROM period_stats p
FULL OUTER JOIN outside_completed oc
  ON oc.plan_year = p.plan_year
 AND oc.plan_month = p.plan_month
 AND oc.project_no = p.project_no

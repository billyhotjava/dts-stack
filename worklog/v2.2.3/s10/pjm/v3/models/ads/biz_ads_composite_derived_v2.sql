{{ config(materialized='table', tags=['project-management-v3', 'biz', 'ads', 'derived', 'composite']) }}

-- 综合域二次指标：对齐二次指标大表 #17-18
-- 依赖: 3 个 derived 表 + progress kpi 表

SELECT
  p.plan_year                                AS period_year,
  p.plan_month                               AS period_month,

  -- 三域健康度评分（来自 derived 表）
  pd.pjm_prog_health_score,
  qd.pjm_qual_health_score,
  td.pjm_tech_health_score,

  -- #17 pjm_composite_health: 项目综合健康度 (<70 红警, <80 黄警)
  -- 公式: 进度×0.4 + 质量×0.3 + 技术状态×0.3
  ROUND(
    COALESCE(pd.pjm_prog_health_score, 0) * 0.4
    + COALESCE(qd.pjm_qual_health_score, 0) * 0.3
    + COALESCE(td.pjm_tech_health_score, 0) * 0.3
  , 2) AS pjm_composite_health,

  -- #18 pjm_risk_warning_index: 风险预警指数 (>10 黄警, >20 红警)
  -- 公式: 里程碑未完成×3 + 高风险未完成×2 + 超期未完成×1
  COALESCE(p.incomplete_milestone_cnt, 0) * 3
    + COALESCE(p.incomplete_high_risk_cnt, 0) * 2
    + COALESCE(p.overdue_incomplete_unchanged_non_general_cnt, 0) * 1
  AS pjm_risk_warning_index,

  -- 预警标志
  CASE WHEN ROUND(
         COALESCE(pd.pjm_prog_health_score, 0) * 0.4
         + COALESCE(qd.pjm_qual_health_score, 0) * 0.3
         + COALESCE(td.pjm_tech_health_score, 0) * 0.3, 2) < 70
       THEN 'red'
       WHEN ROUND(
         COALESCE(pd.pjm_prog_health_score, 0) * 0.4
         + COALESCE(qd.pjm_qual_health_score, 0) * 0.3
         + COALESCE(td.pjm_tech_health_score, 0) * 0.3, 2) < 80
       THEN 'yellow'
       ELSE 'green'
  END AS health_warning_level,

  CASE WHEN (COALESCE(p.incomplete_milestone_cnt, 0) * 3
           + COALESCE(p.incomplete_high_risk_cnt, 0) * 2
           + COALESCE(p.overdue_incomplete_unchanged_non_general_cnt, 0) * 1) > 20
       THEN 'red'
       WHEN (COALESCE(p.incomplete_milestone_cnt, 0) * 3
           + COALESCE(p.incomplete_high_risk_cnt, 0) * 2
           + COALESCE(p.overdue_incomplete_unchanged_non_general_cnt, 0) * 1) > 10
       THEN 'yellow'
       ELSE 'green'
  END AS risk_warning_level

FROM {{ ref('biz_ads_progress_kpi_v2') }} p
LEFT JOIN {{ ref('biz_ads_progress_derived_v2') }} pd
  ON pd.plan_year = p.plan_year AND pd.plan_month = p.plan_month
LEFT JOIN {{ ref('biz_ads_quality_derived_v2') }} qd
  ON qd.period_year = p.plan_year AND qd.period_month = p.plan_month
LEFT JOIN {{ ref('biz_ads_tech_state_derived_v2') }} td
  ON td.period_year = p.plan_year AND td.period_month = p.plan_month
ORDER BY p.plan_year, p.plan_month



-- 质量域二次指标：对齐二次指标大表 #7-11
-- 依赖: biz_ads_quality_kpi_v2

SELECT
  k.period_year,
  k.period_month,

  -- #7 pjm_qual_zero_rate: 归零率 (预警 <60%)
  CASE WHEN k.new_issue_cnt = 0 THEN 0
       ELSE ROUND(k.zero_completed_cnt::numeric / k.new_issue_cnt::numeric * 100, 2)
  END AS pjm_qual_zero_rate,

  -- #8 pjm_qual_remaining_ratio: 现存问题比率 (预警 >50%)
  CASE WHEN k.new_issue_cnt = 0 THEN 0
       ELSE ROUND(k.open_issue_cnt::numeric / k.new_issue_cnt::numeric * 100, 2)
  END AS pjm_qual_remaining_ratio,

  -- #9 pjm_qual_plan_submit_rate: 归零计划提交率 (预警 <80%)
  CASE WHEN k.new_issue_cnt = 0 THEN 0
       ELSE ROUND((k.new_issue_cnt - k.no_zero_plan_cnt)::numeric
                / k.new_issue_cnt::numeric * 100, 2)
  END AS pjm_qual_plan_submit_rate,

  -- #10 pjm_qual_design_defect_ratio: 设计类占比 (预警 >40%)
  CASE WHEN k.open_issue_cnt = 0 THEN 0
       ELSE ROUND(k.open_cat_design::numeric / k.open_issue_cnt::numeric * 100, 2)
  END AS pjm_qual_design_defect_ratio,

  -- #11 pjm_qual_health_score: 质量健康度 (预警 <70)
  -- 公式: 归零率×0.5 + 计划提交率×0.3 + (100-现存率)×0.2
  ROUND(
    (CASE WHEN k.new_issue_cnt = 0 THEN 0
          ELSE k.zero_completed_cnt::numeric / k.new_issue_cnt::numeric * 100
     END) * 0.5
    +
    (CASE WHEN k.new_issue_cnt = 0 THEN 0
          ELSE (k.new_issue_cnt - k.no_zero_plan_cnt)::numeric / k.new_issue_cnt::numeric * 100
     END) * 0.3
    +
    (100 - CASE WHEN k.new_issue_cnt = 0 THEN 0
                ELSE k.open_issue_cnt::numeric / k.new_issue_cnt::numeric * 100
           END) * 0.2
  , 2) AS pjm_qual_health_score,

  -- 预警标志
  CASE WHEN k.new_issue_cnt > 0
        AND k.zero_completed_cnt::numeric / k.new_issue_cnt::numeric * 100 < 60
       THEN true ELSE false
  END AS warn_zero_rate,

  CASE WHEN k.open_issue_cnt > 0
        AND k.open_cat_design::numeric / k.open_issue_cnt::numeric * 100 > 40
       THEN true ELSE false
  END AS warn_design_ratio

FROM "biadmin"."public"."biz_ads_quality_kpi_v2" k
ORDER BY k.period_year, k.period_month
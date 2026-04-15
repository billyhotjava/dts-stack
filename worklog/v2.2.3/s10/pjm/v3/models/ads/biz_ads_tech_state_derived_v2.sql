{{ config(materialized='table', tags=['project-management-v3', 'biz', 'ads', 'derived', 'tech-state']) }}

-- 技术状态域二次指标：对齐二次指标大表 #12-16
-- 依赖: biz_ads_tech_state_kpi_v2

SELECT
  k.period_year,
  k.period_month,

  -- #12 pjm_tech_signature_rate: 变更单签署完成率 (预警 <80%)
  CASE WHEN (k.order_signed_cnt + k.order_unsigned_total) = 0 THEN 0
       ELSE ROUND(k.order_signed_cnt::numeric
                / (k.order_signed_cnt + k.order_unsigned_total)::numeric * 100, 2)
  END AS pjm_tech_signature_rate,

  -- #13 pjm_tech_reform_rate: 整改落实完成率 I,II类 (预警 <80%)
  CASE WHEN (k.reform_done_i_ii + k.reform_pending_i_ii) = 0 THEN 0
       ELSE ROUND(k.reform_done_i_ii::numeric
                / (k.reform_done_i_ii + k.reform_pending_i_ii)::numeric * 100, 2)
  END AS pjm_tech_reform_rate,

  -- #14 pjm_tech_type1_ratio: I类更改占比 (预警 >30%)
  CASE WHEN k.total_change_cnt = 0 THEN 0
       ELSE ROUND(k.new_cat_i::numeric / k.total_change_cnt::numeric * 100, 2)
  END AS pjm_tech_type1_ratio,

  -- #15 pjm_tech_unsigned_ratio: 变更单未签署比率 (预警 >20%)
  CASE WHEN k.total_change_cnt = 0 THEN 0
       ELSE ROUND(k.order_unsigned_total::numeric / k.total_change_cnt::numeric * 100, 2)
  END AS pjm_tech_unsigned_ratio,

  -- #16 pjm_tech_health_score: 技术状态健康度 (预警 <70)
  -- 公式: 签署完成率×0.4 + 整改落实率×0.4 + (100-未签署比率)×0.2
  ROUND(
    (CASE WHEN (k.order_signed_cnt + k.order_unsigned_total) = 0 THEN 0
          ELSE k.order_signed_cnt::numeric
             / (k.order_signed_cnt + k.order_unsigned_total)::numeric * 100
     END) * 0.4
    +
    (CASE WHEN (k.reform_done_i_ii + k.reform_pending_i_ii) = 0 THEN 0
          ELSE k.reform_done_i_ii::numeric
             / (k.reform_done_i_ii + k.reform_pending_i_ii)::numeric * 100
     END) * 0.4
    +
    (100 - CASE WHEN k.total_change_cnt = 0 THEN 0
                ELSE k.order_unsigned_total::numeric / k.total_change_cnt::numeric * 100
           END) * 0.2
  , 2) AS pjm_tech_health_score,

  -- 预警标志
  CASE WHEN (k.order_signed_cnt + k.order_unsigned_total) > 0
        AND k.order_signed_cnt::numeric
          / (k.order_signed_cnt + k.order_unsigned_total)::numeric * 100 < 80
       THEN true ELSE false
  END AS warn_signature_rate,

  CASE WHEN (k.reform_done_i_ii + k.reform_pending_i_ii) > 0
        AND k.reform_done_i_ii::numeric
          / (k.reform_done_i_ii + k.reform_pending_i_ii)::numeric * 100 < 80
       THEN true ELSE false
  END AS warn_reform_rate

FROM {{ ref('biz_ads_tech_state_kpi_v2') }} k
ORDER BY k.period_year, k.period_month

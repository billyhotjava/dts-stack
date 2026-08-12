{{ config(materialized='table', tags=['project-management-v3', 'biz', 'ads', 'derived', 'budget']) }}

-- 预算域二次指标（每个业务快照日一行）
-- 依赖: biz_ads_budget_kpi_v2

SELECT
  k.snapshot_scope,
  -- pjm_budg_execution_rate: 预算执行率 = 已执行 / 预算 (预警 >90%, 红线 ≥100% 超支)
  CASE WHEN k.total_budget = 0 THEN 0
       ELSE ROUND(k.total_executed::numeric / k.total_budget::numeric * 100, 2)
  END AS pjm_budg_execution_rate,

  -- pjm_budg_book_rate: 账面入账率 = 账面成本 / 预算 (已确认支出占预算)
  CASE WHEN k.total_budget = 0 THEN 0
       ELSE ROUND(k.total_book_cost::numeric / k.total_budget::numeric * 100, 2)
  END AS pjm_budg_book_rate,

  -- pjm_budg_payable_ratio: 应付占比 = 应付账款 / 已执行 (在途未付压力, 预警 >30%)
  CASE WHEN k.total_executed = 0 THEN 0
       ELSE ROUND(k.total_payable::numeric / k.total_executed::numeric * 100, 2)
  END AS pjm_budg_payable_ratio,

  -- pjm_budg_remaining_rate: 预算剩余率 = 剩余 / 预算
  CASE WHEN k.total_budget = 0 THEN 0
       ELSE ROUND(k.total_remaining::numeric / k.total_budget::numeric * 100, 2)
  END AS pjm_budg_remaining_rate,

  -- pjm_budg_health_score: 预算健康度 (预警 <70)
  -- 公式: 预算控制(无超支)×0.7 + 资金支付健康(应付占比低)×0.3
  ROUND(
    (100 - LEAST(100, CASE WHEN k.total_budget = 0 THEN 0
                           ELSE k.overrun_amount::numeric / k.total_budget::numeric * 100 END)) * 0.7
    +
    (100 - LEAST(100, CASE WHEN k.total_executed = 0 THEN 0
                           ELSE k.total_payable::numeric / k.total_executed::numeric * 100 END)) * 0.3
  , 2) AS pjm_budg_health_score,

  -- 预警标志
  CASE WHEN k.total_budget > 0
        AND k.total_executed::numeric / k.total_budget::numeric * 100 >= 100
       THEN true ELSE false
  END AS warn_overrun,

  CASE WHEN k.overrun_item_cnt > 0 THEN true ELSE false END AS warn_overrun_items,
  k.snapshot_date

FROM {{ ref('biz_ads_budget_kpi_v2') }} k

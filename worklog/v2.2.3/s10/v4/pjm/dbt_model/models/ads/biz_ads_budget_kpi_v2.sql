{{ config(materialized='table', tags=['project-management-v3', 'biz', 'ads', 'kpi', 'budget']) }}

-- 预算域 KPI（当前快照，单行总量）
-- 快照口径：无 period 维度，反映当前预算执行状态。大屏明细卡片可直接按 dept/project 过滤 biz_dws_budget_v2。

SELECT
  SUM(s.item_cnt)                                                                 AS item_cnt,
  SUM(s.budget_amount)                                                            AS total_budget,
  SUM(s.prepaid_amount)                                                           AS total_prepaid,
  SUM(s.book_cost_amount)                                                         AS total_book_cost,
  SUM(s.payable_amount)                                                           AS total_payable,
  SUM(s.executed_amount)                                                          AS total_executed,
  SUM(s.remaining_amount)                                                         AS total_remaining,
  SUM(s.overrun_amount)                                                           AS overrun_amount,
  SUM(s.overrun_item_cnt)                                                         AS overrun_item_cnt
FROM {{ ref('biz_dws_budget_v2') }} s

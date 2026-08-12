{{ config(materialized='table', tags=['project-management-v3', 'biz', 'ads', 'kpi', 'budget']) }}

-- 预算域 KPI（每个业务快照日一行）

SELECT
  'ALL'::text AS snapshot_scope,
  SUM(s.item_cnt)                                                                 AS item_cnt,
  SUM(s.budget_amount)                                                            AS total_budget,
  SUM(s.prepaid_amount)                                                           AS total_prepaid,
  SUM(s.book_cost_amount)                                                         AS total_book_cost,
  SUM(s.payable_amount)                                                           AS total_payable,
  SUM(s.executed_amount)                                                          AS total_executed,
  SUM(s.remaining_amount)                                                         AS total_remaining,
  SUM(s.overrun_amount)                                                           AS overrun_amount,
  SUM(s.overrun_item_cnt)                                                         AS overrun_item_cnt,
  s.snapshot_date
FROM {{ ref('biz_dws_budget_v2') }} s
GROUP BY s.snapshot_date

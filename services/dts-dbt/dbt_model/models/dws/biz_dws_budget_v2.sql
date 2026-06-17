{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dws', 'budget']) }}

-- 预算域汇总（当前快照，无时间轴）
-- 粒度: project_no × research_lab
-- 下游可按 project_no / research_lab 再聚合；子课题级与明细级读 biz_dwd_budget_v2

SELECT
  d.project_no,
  d.research_lab,

  COUNT(*)                                                                        AS item_cnt,

  SUM(d.budget_amount)                                                            AS budget_amount,
  SUM(d.prepaid_amount)                                                           AS prepaid_amount,
  SUM(d.book_cost_amount)                                                         AS book_cost_amount,
  SUM(d.payable_amount)                                                           AS payable_amount,
  SUM(d.executed_amount)                                                          AS executed_amount,
  SUM(d.remaining_amount)                                                         AS remaining_amount,

  -- 超支：单条 max(0,已执行-预算) 之和；以及超支条目数
  SUM(d.overrun_amount)                                                           AS overrun_amount,
  SUM(CASE WHEN d.is_overrun THEN 1 ELSE 0 END)                                   AS overrun_item_cnt

FROM {{ ref('biz_dwd_budget_v2') }} d
GROUP BY d.project_no, d.research_lab

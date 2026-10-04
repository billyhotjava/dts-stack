

-- 预算事实表 — 科研经费三本账快照
-- 已执行 = 预付账款 + 账面成本 + 应付账款（口径冻结：三项合计）
-- 无枚举/分类字段，不需要 canonical/alias dim；研究室直接取值（对齐其他域 dept）

WITH stg AS (
  SELECT * FROM "biadmin"."public"."stg_pm__budget_v2"
  WHERE project_no IS NOT NULL
),
derived AS (
  SELECT
    s.*,
    COALESCE(s.budget_amount, 0)                                                  AS budget_amount_f,
    (COALESCE(s.prepaid_amount, 0)
      + COALESCE(s.book_cost_amount, 0)
      + COALESCE(s.payable_amount, 0))                                            AS executed_amount
  FROM stg s
)

SELECT
  concat('budget:', d.source_row_id) AS budget_id,

  d.source_row_id,
  d.source_table,
  d.source_system,
  d.imported_at AS source_imported_at,

  d.project_no,
  d.budget_no,
  d.subtopic,
  d.research_lab,

  -- 三本账原始金额（NULL 归 0 便于汇总）
  COALESCE(d.budget_amount, 0)        AS budget_amount,
  COALESCE(d.prepaid_amount, 0)       AS prepaid_amount,
  COALESCE(d.book_cost_amount, 0)     AS book_cost_amount,
  COALESCE(d.payable_amount, 0)       AS payable_amount,

  -- 派生口径
  d.executed_amount,
  (d.budget_amount_f - d.executed_amount)                                         AS remaining_amount,
  GREATEST(0, d.executed_amount - d.budget_amount_f)                              AS overrun_amount,
  CASE WHEN d.executed_amount > d.budget_amount_f THEN true ELSE false END        AS is_overrun,
  CASE WHEN d.budget_amount_f = 0 THEN NULL
       ELSE ROUND(d.executed_amount / d.budget_amount_f * 100, 2)
  END AS execution_rate_line,

  now() AS etl_time
FROM derived d
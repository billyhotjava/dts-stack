{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'tech-state']) }}

-- 技术状态域周期汇总：按 (year, month, project_no, dept) 聚合
-- 对应 PDF P4 技术状态统计表的全部指标

WITH states AS (
  SELECT * FROM {{ ref('biz_dwd_tech_state') }}
),
measures AS (
  SELECT
    project_no,
    tech_state_name,
    COUNT(*) AS measure_cnt
  FROM {{ ref('biz_dwd_tech_state_measure') }}
  GROUP BY project_no, tech_state_name
)

SELECT
  s.submit_year                                       AS period_year,
  s.submit_quarter                                    AS period_quarter,
  s.submit_month                                      AS period_month,
  s.project_no,
  s.dept,

  -- === 变更总量 ===
  COUNT(*)                                            AS total_change_cnt,

  -- === 按更改类别 ===
  SUM(CASE WHEN s.change_category = 'I' THEN 1 ELSE 0 END)    AS cat_i_cnt,
  SUM(CASE WHEN s.change_category = 'II' THEN 1 ELSE 0 END)   AS cat_ii_cnt,
  SUM(CASE WHEN s.change_category = 'III' THEN 1 ELSE 0 END)  AS cat_iii_cnt,

  -- === 签署状态统计 ===
  SUM(CASE WHEN s.is_signed THEN 1 ELSE 0 END)                AS signed_cnt,
  SUM(CASE WHEN s.is_reviewed AND NOT s.is_signed THEN 1 ELSE 0 END) AS reviewing_cnt,
  SUM(CASE WHEN NOT s.is_reviewed AND NOT s.is_signed THEN 1 ELSE 0 END) AS unsigned_cnt,

  -- === 签署完成率 ===
  CASE
    WHEN COUNT(*) = 0 THEN 0
    ELSE ROUND(
      SUM(CASE WHEN s.is_signature_completed THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END AS signature_completion_rate,

  -- === 闭环统计 ===
  SUM(CASE WHEN s.is_closed THEN 1 ELSE 0 END)                AS closed_cnt,
  SUM(CASE WHEN NOT s.is_closed THEN 1 ELSE 0 END)            AS open_cnt,

  -- === 整改落实统计 ===
  SUM(CASE WHEN s.is_reform_done THEN 1 ELSE 0 END)           AS reform_done_cnt,
  SUM(CASE WHEN s.is_reform_not_applicable THEN 1 ELSE 0 END) AS reform_na_cnt,
  SUM(CASE WHEN NOT s.is_reform_done AND NOT s.is_reform_not_applicable THEN 1 ELSE 0 END) AS reform_pending_cnt,

  -- === 文件签署状态组合（PDF P4 细分指标）===
  -- 文件未完成签署 (I II类)
  SUM(CASE WHEN s.change_category IN ('I', 'II') AND NOT s.is_signature_completed THEN 1 ELSE 0 END)
    AS file_unsigned_i_ii_cnt,
  -- 文件未完成签署 (III类)
  SUM(CASE WHEN s.change_category = 'III' AND NOT s.is_signature_completed THEN 1 ELSE 0 END)
    AS file_unsigned_iii_cnt,
  -- 文件已完成签署 (I II III类)
  SUM(CASE WHEN s.is_signature_completed THEN 1 ELSE 0 END)
    AS file_signed_cnt,

  -- === 变更单未完成签署统计 ===
  SUM(CASE WHEN s.change_category = 'I' AND NOT s.is_signature_completed THEN 1 ELSE 0 END)
    AS change_unsigned_i_cnt,
  SUM(CASE WHEN s.change_category = 'II' AND NOT s.is_signature_completed THEN 1 ELSE 0 END)
    AS change_unsigned_ii_cnt,
  SUM(CASE WHEN s.change_category = 'III' AND NOT s.is_signature_completed THEN 1 ELSE 0 END)
    AS change_unsigned_iii_cnt,

  -- === 变更单已完成签署 ===
  SUM(CASE WHEN s.is_signature_completed THEN 1 ELSE 0 END)
    AS change_signed_cnt,

  -- === 未落实整改 (I II类) ===
  SUM(CASE WHEN s.change_category IN ('I', 'II')
       AND NOT s.is_reform_done AND NOT s.is_reform_not_applicable THEN 1 ELSE 0 END)
    AS unreformed_i_ii_cnt,
  -- === 已落实整改 (I II类) ===
  SUM(CASE WHEN s.change_category IN ('I', 'II') AND s.is_reform_done
       AND NOT s.is_reform_not_applicable THEN 1 ELSE 0 END)
    AS reformed_i_ii_cnt,
  -- === 不涉及(III类)整改 ===
  SUM(CASE WHEN s.is_reform_not_applicable THEN 1 ELSE 0 END)
    AS reform_na_iii_cnt,

  -- === 措施覆盖率 ===
  SUM(CASE WHEN m.measure_cnt > 0 THEN 1 ELSE 0 END)         AS has_measure_cnt,
  CASE
    WHEN COUNT(*) = 0 THEN 0
    ELSE ROUND(
      SUM(CASE WHEN m.measure_cnt > 0 THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END AS measure_coverage_rate

FROM states s
LEFT JOIN measures m
  ON m.project_no = s.project_no AND m.tech_state_name = s.tech_state_name
WHERE s.submit_year IS NOT NULL
GROUP BY s.submit_year, s.submit_quarter, s.submit_month, s.project_no, s.dept

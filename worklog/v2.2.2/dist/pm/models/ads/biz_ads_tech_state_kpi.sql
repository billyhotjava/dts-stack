{{ config(materialized='table', tags=['project-management', 'biz', 'ads', 'kpi', 'tech-state']) }}

-- 技术状态域 KPI 汇总
-- 对应 PDF P4 技术状态统计 + T02 管控层指标矩阵
-- 粒度: (period_year, period_month)

SELECT
  period_year,
  period_month,

  -- === 变更总量 ===
  SUM(total_change_cnt)                                AS total_change_cnt,

  -- === 按更改类别 ===
  SUM(cat_i_cnt)                                       AS cat_i_cnt,
  SUM(cat_ii_cnt)                                      AS cat_ii_cnt,
  SUM(cat_iii_cnt)                                     AS cat_iii_cnt,

  -- === 签署统计 ===
  SUM(signed_cnt)                                      AS signed_cnt,
  SUM(reviewing_cnt)                                   AS reviewing_cnt,
  SUM(unsigned_cnt)                                    AS unsigned_cnt,

  -- === 签署完成率 ===
  CASE
    WHEN SUM(total_change_cnt) = 0 THEN 0
    ELSE ROUND(SUM(signed_cnt)::numeric / SUM(total_change_cnt)::numeric, 4)
  END AS signature_completion_rate,

  -- === 闭环统计 ===
  SUM(closed_cnt)                                      AS closed_cnt,
  SUM(open_cnt)                                        AS open_cnt,

  -- === 整改统计 ===
  SUM(reform_done_cnt)                                 AS reform_done_cnt,
  SUM(reform_na_cnt)                                   AS reform_na_cnt,
  SUM(reform_pending_cnt)                              AS reform_pending_cnt,

  -- === PDF P4 细分指标 ===
  SUM(file_unsigned_i_ii_cnt)                          AS file_unsigned_i_ii_cnt,
  SUM(file_unsigned_iii_cnt)                           AS file_unsigned_iii_cnt,
  SUM(file_signed_cnt)                                 AS file_signed_cnt,
  SUM(change_unsigned_i_cnt)                           AS change_unsigned_i_cnt,
  SUM(change_unsigned_ii_cnt)                          AS change_unsigned_ii_cnt,
  SUM(change_unsigned_iii_cnt)                         AS change_unsigned_iii_cnt,
  SUM(change_signed_cnt)                               AS change_signed_cnt,
  SUM(unreformed_i_ii_cnt)                             AS unreformed_i_ii_cnt,
  SUM(reformed_i_ii_cnt)                               AS reformed_i_ii_cnt,
  SUM(reform_na_iii_cnt)                               AS reform_na_iii_cnt,

  -- === 措施覆盖率 ===
  SUM(has_measure_cnt)                                 AS has_measure_cnt,
  CASE
    WHEN SUM(total_change_cnt) = 0 THEN 0
    ELSE ROUND(SUM(has_measure_cnt)::numeric / SUM(total_change_cnt)::numeric, 4)
  END AS measure_coverage_rate

FROM {{ ref('biz_dws_tech_state_period_summary') }}
GROUP BY period_year, period_month
ORDER BY period_year, period_month

{{ config(materialized='table', tags=['project-management', 'biz', 'ads', 'kpi', 'tech-state']) }}

SELECT
  s.period_year,
  s.period_month,

  SUM(s.total_change_cnt)                                                        AS total_change_cnt,

  SUM(s.cat_i)                                                                   AS cat_i,
  SUM(s.cat_ii)                                                                  AS cat_ii,
  SUM(s.cat_iii)                                                                 AS cat_iii,

  SUM(s.signature_completed_cnt)                                                 AS signature_completed_cnt,
  SUM(s.file_signed_cnt)                                                         AS file_signed_cnt,
  SUM(s.reform_done_cnt)                                                         AS reform_done_cnt,
  SUM(s.reform_pending_cnt)                                                      AS reform_pending_cnt

FROM {{ ref('biz_dws_tech_state_period_summary') }} s
GROUP BY s.period_year, s.period_month
ORDER BY s.period_year, s.period_month

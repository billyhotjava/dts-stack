{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'tech-state']) }}

SELECT
  EXTRACT(YEAR FROM d.change_submit_time)::int                                   AS period_year,
  to_char(d.change_submit_time, 'YYYY-MM')                                       AS period_month,
  d.project_no,
  d.dept,

  COUNT(*)                                                                       AS total_change_cnt,

  -- 按 change_category 分类
  SUM(CASE WHEN d.change_category = 'I' THEN 1 ELSE 0 END)                      AS cat_i,
  SUM(CASE WHEN d.change_category = 'II' THEN 1 ELSE 0 END)                    AS cat_ii,
  SUM(CASE WHEN d.change_category = 'III' THEN 1 ELSE 0 END)                   AS cat_iii,

  -- 签署完成
  SUM(CASE WHEN d.is_signature_completed THEN 1 ELSE 0 END)                     AS signature_completed_cnt,

  -- 文件签署
  SUM(CASE WHEN d.is_file_signed THEN 1 ELSE 0 END)                             AS file_signed_cnt,

  -- 整改落实
  SUM(CASE WHEN d.is_reformed THEN 1 ELSE 0 END)                                AS reform_done_cnt,
  SUM(CASE WHEN NOT d.is_reformed THEN 1 ELSE 0 END)                            AS reform_pending_cnt

FROM {{ ref('biz_dwd_tech_state') }} d
WHERE d.change_submit_time IS NOT NULL
GROUP BY
  EXTRACT(YEAR FROM d.change_submit_time)::int,
  to_char(d.change_submit_time, 'YYYY-MM'),
  d.project_no,
  d.dept

{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dws', 'tech-state']) }}

-- 技术状态域月度汇总：对齐原始指标大表 #49-67
-- 粒度: project_no × dept × submit_month
-- 所有分类基于 DWD 布尔字段（字典派生），不再硬编码中文枚举

SELECT
  d.submit_year                                                                     AS period_year,
  d.submit_month                                                                    AS period_month,
  d.project_no,
  d.dept,

  COUNT(*)                                                                          AS total_change_cnt,

  -- #49-51 新增更改按类别（字典派生布尔）
  SUM(CASE WHEN d.is_cat_i   THEN 1 ELSE 0 END)                                     AS new_cat_i,
  SUM(CASE WHEN d.is_cat_ii  THEN 1 ELSE 0 END)                                     AS new_cat_ii,
  SUM(CASE WHEN d.is_cat_iii THEN 1 ELSE 0 END)                                     AS new_cat_iii,

  -- 需求提出口径（"已提出需求" = 文件签署字典有命中）
  SUM(CASE WHEN btrim(COALESCE(d.file_signature_status,'')) <> '' THEN 1 ELSE 0 END)
                                                                                    AS requirement_submitted_cnt,
  SUM(CASE WHEN btrim(COALESCE(d.file_signature_status,'')) = ''  THEN 1 ELSE 0 END)
                                                                                    AS requirement_not_submitted_cnt,
  -- "已评估评审" = I/II 类且字典 is_file_reviewed=true
  SUM(CASE WHEN (d.is_cat_i OR d.is_cat_ii) AND d.is_file_reviewed THEN 1 ELSE 0 END) AS review_done_cnt,
  -- "未评估评审" = I/II 类且字典 is_file_reviewed=false
  SUM(CASE WHEN (d.is_cat_i OR d.is_cat_ii) AND NOT d.is_file_reviewed THEN 1 ELSE 0 END) AS review_pending_cnt,

  -- #52-56 文件签署状态 5 种 — 字典派生 + 类别组合
  -- I/II 类已提出未评审
  SUM(CASE WHEN (d.is_cat_i OR d.is_cat_ii) AND NOT d.is_file_reviewed AND NOT d.is_file_signed
                 AND btrim(COALESCE(d.file_signature_status,'')) <> ''
           THEN 1 ELSE 0 END)                                                       AS file_submitted_not_reviewed_i_ii,
  -- I/II 类已评审未签署
  SUM(CASE WHEN (d.is_cat_i OR d.is_cat_ii) AND d.is_file_reviewed AND NOT d.is_file_signed
           THEN 1 ELSE 0 END)                                                       AS file_reviewed_not_signed_i_ii,
  -- I/II 类已评审已签署
  SUM(CASE WHEN (d.is_cat_i OR d.is_cat_ii) AND d.is_file_reviewed AND d.is_file_signed
           THEN 1 ELSE 0 END)                                                       AS file_reviewed_signed_i_ii,
  -- III 类已提出未签署
  SUM(CASE WHEN d.is_cat_iii AND NOT d.is_file_signed
                 AND btrim(COALESCE(d.file_signature_status,'')) <> ''
           THEN 1 ELSE 0 END)                                                       AS file_submitted_not_signed_iii,
  -- III 类已提出已签署
  SUM(CASE WHEN d.is_cat_iii AND d.is_file_signed
           THEN 1 ELSE 0 END)                                                       AS file_submitted_signed_iii,

  -- #60-63 变更单签署状态按类别（由 is_signature_completed — completion_signature='是' 派生）
  SUM(CASE WHEN d.is_cat_i   AND NOT d.is_signature_completed THEN 1 ELSE 0 END)   AS order_unsigned_cat_i,
  SUM(CASE WHEN d.is_cat_ii  AND NOT d.is_signature_completed THEN 1 ELSE 0 END)   AS order_unsigned_cat_ii,
  SUM(CASE WHEN d.is_cat_iii AND NOT d.is_signature_completed THEN 1 ELSE 0 END)   AS order_unsigned_cat_iii,

  -- #64 变更单已完成签署
  SUM(CASE WHEN d.is_signature_completed THEN 1 ELSE 0 END)                         AS order_signed_cnt,

  -- #65-67 整改落实状态（DWD 布尔）
  SUM(CASE WHEN (d.is_cat_i OR d.is_cat_ii) AND d.is_reform_pending THEN 1 ELSE 0 END) AS reform_pending_i_ii,
  SUM(CASE WHEN (d.is_cat_i OR d.is_cat_ii) AND d.is_reform_done    THEN 1 ELSE 0 END) AS reform_done_i_ii,
  SUM(CASE WHEN d.is_cat_iii AND d.is_reform_na                     THEN 1 ELSE 0 END) AS reform_na_iii

FROM {{ ref('biz_dwd_tech_state_v2') }} d
WHERE d.submit_month IS NOT NULL
GROUP BY d.submit_year, d.submit_month, d.project_no, d.dept

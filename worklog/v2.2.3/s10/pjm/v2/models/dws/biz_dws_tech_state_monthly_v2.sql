{{ config(materialized='table', tags=['project-management-v2', 'biz', 'dws', 'tech-state']) }}

-- 技术状态域月度汇总：对齐原始指标大表 #49-67
-- 粒度: project_no × dept × submit_month
-- 存储全部计数字段，支持跨月查询

SELECT
  d.submit_year                                                                     AS period_year,
  d.submit_month                                                                    AS period_month,
  d.project_no,
  d.dept,

  -- 总数
  COUNT(*)                                                                          AS total_change_cnt,

  -- #49-51 新增更改按类别
  SUM(CASE WHEN d.change_category = 'I' THEN 1 ELSE 0 END)                         AS new_cat_i,
  SUM(CASE WHEN d.change_category = 'II' THEN 1 ELSE 0 END)                        AS new_cat_ii,
  SUM(CASE WHEN d.change_category = 'III' THEN 1 ELSE 0 END)                       AS new_cat_iii,

  -- === 需求提出口径（v3 科室变更评估评审看板用）===
  -- "已提出需求" = file_signature_status 非空（枚举值均以"已提出需求"或"已评估评审"起始）
  -- "未提出需求" = file_signature_status IS NULL 或空串（变更单已创建但尚未进入签署流程）
  SUM(CASE WHEN btrim(COALESCE(d.file_signature_status,'')) <> '' THEN 1 ELSE 0 END)
                                                                                    AS requirement_submitted_cnt,
  SUM(CASE WHEN btrim(COALESCE(d.file_signature_status,'')) = '' THEN 1 ELSE 0 END)
                                                                                    AS requirement_not_submitted_cnt,
  -- "已评估评审" = I/II 类且 file_signature_status 在已评估的两个状态之一
  SUM(CASE WHEN d.change_category IN ('I','II')
           AND d.file_signature_status IN ('已评估评审，未签署','已评估评审，已签署')
           THEN 1 ELSE 0 END)                                                      AS review_done_cnt,
  -- "未评估评审" = I/II 类且 file_signature_status 尚未进入评审
  SUM(CASE WHEN d.change_category IN ('I','II')
           AND (d.file_signature_status IS NULL
                OR d.file_signature_status = '已提出需求，未评估评审')
           THEN 1 ELSE 0 END)                                                      AS review_pending_cnt,

  -- #52-56 文件签署状态 5 种 (TBD-4)
  SUM(CASE WHEN d.change_category IN ('I','II')
           AND d.file_signature_status = '已提出需求，未评估评审'
           THEN 1 ELSE 0 END)                                                      AS file_submitted_not_reviewed_i_ii,
  SUM(CASE WHEN d.change_category IN ('I','II')
           AND d.file_signature_status = '已评估评审，未签署'
           THEN 1 ELSE 0 END)                                                      AS file_reviewed_not_signed_i_ii,
  SUM(CASE WHEN d.change_category IN ('I','II')
           AND d.file_signature_status = '已评估评审，已签署'
           THEN 1 ELSE 0 END)                                                      AS file_reviewed_signed_i_ii,
  SUM(CASE WHEN d.change_category = 'III'
           AND d.file_signature_status = '已提出需求，未签署'
           THEN 1 ELSE 0 END)                                                      AS file_submitted_not_signed_iii,
  SUM(CASE WHEN d.change_category = 'III'
           AND d.file_signature_status = '已提出需求，已签署'
           THEN 1 ELSE 0 END)                                                      AS file_submitted_signed_iii,

  -- #57 文件未完成签署（I,II类）= #52 + #53
  -- #58 文件未完成签署（III类）= #55 (TBD-5: 与#55重复，复用 file_submitted_not_signed_iii)
  -- #59 文件已完成签署（I,II,III类）= #54 + #56
  -- 以上由 ADS 层计算，此处提供原子字段即可

  -- #60-63 变更单签署状态按类别
  SUM(CASE WHEN d.change_category = 'I' AND NOT d.is_signature_completed
           THEN 1 ELSE 0 END)                                                      AS order_unsigned_cat_i,
  SUM(CASE WHEN d.change_category = 'II' AND NOT d.is_signature_completed
           THEN 1 ELSE 0 END)                                                      AS order_unsigned_cat_ii,
  SUM(CASE WHEN d.change_category = 'III' AND NOT d.is_signature_completed
           THEN 1 ELSE 0 END)                                                      AS order_unsigned_cat_iii,

  -- #64 变更单已完成签署
  SUM(CASE WHEN d.is_signature_completed THEN 1 ELSE 0 END)                        AS order_signed_cnt,

  -- #65-67 整改落实状态
  SUM(CASE WHEN d.change_category IN ('I','II')
           AND btrim(COALESCE(d.reform_status, '')) NOT IN ('已落实整改', '不涉及', '')
           AND d.reform_status IS NOT NULL
           THEN 1 ELSE 0 END)                                                      AS reform_pending_i_ii,
  SUM(CASE WHEN d.change_category IN ('I','II')
           AND d.reform_status = '已落实整改'
           THEN 1 ELSE 0 END)                                                      AS reform_done_i_ii,
  SUM(CASE WHEN d.change_category = 'III'
           AND d.reform_status = '不涉及'
           THEN 1 ELSE 0 END)                                                      AS reform_na_iii

FROM {{ ref('biz_dwd_tech_state_v2') }} d
WHERE d.submit_year IS NOT NULL
GROUP BY d.submit_year, d.submit_month, d.project_no, d.dept

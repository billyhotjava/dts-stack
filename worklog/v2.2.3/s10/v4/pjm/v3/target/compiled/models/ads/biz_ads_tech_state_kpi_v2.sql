

-- 技术状态域原始指标 ADS：对齐原始指标大表 #49-67
-- 粒度: period_month（跨项目/科室聚合）

SELECT
  s.period_year,
  s.period_month,

  -- 总数
  SUM(s.total_change_cnt)                    AS total_change_cnt,

  -- #49-51 新增更改按类别
  SUM(s.new_cat_i)                           AS new_cat_i,
  SUM(s.new_cat_ii)                          AS new_cat_ii,
  SUM(s.new_cat_iii)                         AS new_cat_iii,

  -- #52-56 文件签署状态 5 种
  SUM(s.file_submitted_not_reviewed_i_ii)    AS file_submitted_not_reviewed_i_ii,
  SUM(s.file_reviewed_not_signed_i_ii)       AS file_reviewed_not_signed_i_ii,
  SUM(s.file_reviewed_signed_i_ii)           AS file_reviewed_signed_i_ii,
  SUM(s.file_submitted_not_signed_iii)       AS file_submitted_not_signed_iii,
  SUM(s.file_submitted_signed_iii)           AS file_submitted_signed_iii,

  -- #57 文件未完成签署（I,II类）= #52 + #53
  SUM(s.file_submitted_not_reviewed_i_ii)
    + SUM(s.file_reviewed_not_signed_i_ii)   AS file_unsigned_i_ii,
  -- #58 文件未完成签署（III类）= #55
  SUM(s.file_submitted_not_signed_iii)       AS file_unsigned_iii,
  -- #59 文件已完成签署（I,II,III类）= #54 + #56
  SUM(s.file_reviewed_signed_i_ii)
    + SUM(s.file_submitted_signed_iii)       AS file_signed_total,

  -- #60-63 变更单签署
  SUM(s.order_unsigned_cat_i)                AS order_unsigned_cat_i,
  SUM(s.order_unsigned_cat_ii)               AS order_unsigned_cat_ii,
  SUM(s.order_unsigned_cat_iii)              AS order_unsigned_cat_iii,
  SUM(s.order_unsigned_cat_i) + SUM(s.order_unsigned_cat_ii)
    + SUM(s.order_unsigned_cat_iii)          AS order_unsigned_total,
  -- #64 已完成签署
  SUM(s.order_signed_cnt)                    AS order_signed_cnt,

  -- #65-67 整改落实
  SUM(s.reform_pending_i_ii)                 AS reform_pending_i_ii,
  SUM(s.reform_done_i_ii)                    AS reform_done_i_ii,
  SUM(s.reform_na_iii)                       AS reform_na_iii

FROM "biadmin"."public"."biz_dws_tech_state_monthly_v2" s
GROUP BY s.period_year, s.period_month
ORDER BY s.period_year, s.period_month
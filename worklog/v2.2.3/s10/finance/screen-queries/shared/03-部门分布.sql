-- ============================================================
-- 卡片名称：按部门分布（环形图）
-- 用途：展示各部门资金往来规模（取绝对值，不区分借贷方向）
-- 所属大屏：F3 个人辅助余额大屏
-- 数据表：biz_dws_aux_balance_personal_by_dept（DWS 层）
-- ============================================================

SELECT
    employee_dept,              -- 部门名称
    net_balance,                -- 部门净余额（借方 - 贷方）
    debit_total,                -- 部门借方合计（正数余额之和）
    credit_total,               -- 部门贷方合计（负数余额绝对值之和）
    abs_total,                  -- 部门资金往来规模（余额绝对值之和，用于环形图）
    employee_count,             -- 部门涉及职工数（去重）
    subject_count,              -- 部门涉及科目数（去重）
    record_count                -- 部门记录数
FROM biz_dws_aux_balance_personal_by_dept
ORDER BY abs_total DESC;

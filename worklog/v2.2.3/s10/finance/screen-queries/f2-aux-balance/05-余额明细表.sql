-- ============================================================
-- 卡片名称：辅助余额明细表
-- 用途：展示辅助余额全量明细，含费用分类和合同标记
-- 所属大屏：F2 辅助余额大屏
-- 数据表：biz_dwd_aux_balance（DWD 层）
-- ============================================================

SELECT
    subject_code,               -- 科目编号（如 5001.01）
    subject_name,               -- 科目名称（如 原材料-钢材）
    dept_name,                  -- 部门名称
    contract_name,              -- 合同名称（无合同时为 '—'）
    balance,                    -- 余额（元）
    expense_category,           -- 费用类别（派生，按科目编号前缀归类）
    has_contract                -- 是否有效合同（布尔值，排除 NULL 和 '—'）
FROM biz_dwd_aux_balance
ORDER BY subject_code, dept_name;

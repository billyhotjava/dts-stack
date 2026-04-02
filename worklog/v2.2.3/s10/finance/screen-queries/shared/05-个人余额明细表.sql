-- ============================================================
-- 卡片名称：个人辅助余额明细表
-- 用途：展示个人辅助余额全量明细，含借贷方向和科目分类
-- 所属大屏：F3 个人辅助余额大屏
-- 数据表：biz_dwd_aux_balance_personal（DWD 层）
-- ============================================================

SELECT
    subject_code,               -- 科目编号（如 1122.01、2211.01）
    subject_name,               -- 科目名称（如 备用金、工资应付）
    employee_dept,              -- 职工部门
    employee_name,              -- 职工名称
    balance,                    -- 余额（元，正=借方/应收，负=贷方/应付）
    balance_direction,          -- 借贷方向（debit/credit/zero）
    abs_balance,                -- 余额绝对值（元，方便汇总）
    subject_category            -- 科目类别（其他应收-借款 / 应付职工薪酬 / 其他）
FROM biz_dwd_aux_balance_personal
ORDER BY employee_name, subject_code;

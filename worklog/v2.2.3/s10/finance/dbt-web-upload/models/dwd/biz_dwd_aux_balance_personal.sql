-- biz_dwd_aux_balance_personal.sql
-- DWD层：个人辅助余额明细，添加借贷方向(debit_credit)和科目类别(subject_category)
-- 源表: public.ods_finance_aux_balance_personal
-- 注意: CSV导入后所有列为TEXT，此处对balance转型为NUMERIC

SELECT
    subject_code,
    subject_name,
    employee_dept,
    employee_name,
    balance::NUMERIC(15,2) AS balance,

    -- 借贷方向：正数=借方(应收/借款)，负数=贷方(应付/代扣)
    CASE
        WHEN balance::NUMERIC > 0 THEN 'debit'
        WHEN balance::NUMERIC < 0 THEN 'credit'
        ELSE 'zero'
    END AS debit_credit,

    -- 科目类别
    CASE
        WHEN subject_code LIKE '1122%' THEN '其他应收-借款'
        WHEN subject_code LIKE '2211%' THEN '应付职工薪酬'
        ELSE '其他'
    END AS subject_category

FROM public.ods_finance_aux_balance_personal

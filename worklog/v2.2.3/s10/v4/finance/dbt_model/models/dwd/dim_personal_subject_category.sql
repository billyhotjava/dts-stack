{{ config(materialized='table', tags=['finance', 'dim', 'dwd']) }}

SELECT subject_category_id, code, raw_value, label, sort_order
FROM (
  VALUES
    ('personal_subject_borrow', 'RECEIVABLE_LOAN', '其他应收-借款', '其他应收-借款', 1),
    ('personal_subject_payroll', 'PAYROLL_PAYABLE', '应付职工薪酬', '应付职工薪酬', 2),
    ('personal_subject_other', 'OTHER', '其他', '其他', 3)
) AS t(subject_category_id, code, raw_value, label, sort_order)

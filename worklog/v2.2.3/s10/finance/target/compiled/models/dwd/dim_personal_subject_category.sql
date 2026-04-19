

SELECT subject_category_id, code, label, sort_order
FROM (
  VALUES
    ('personal_subject_borrow', '其他应收-借款', '其他应收-借款', 1),
    ('personal_subject_payroll', '应付职工薪酬', '应付职工薪酬', 2),
    ('personal_subject_other', '其他', '其他', 3)
) AS t(subject_category_id, code, label, sort_order)
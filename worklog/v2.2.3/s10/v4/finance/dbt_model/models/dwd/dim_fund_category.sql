{{ config(materialized='table', tags=['finance', 'dim', 'dwd']) }}

SELECT fund_category_id, code, raw_value, label, is_career, is_welfare, is_safety, sort_order
FROM (
  VALUES
    ('own_fund_category_career',  'PUBLIC_INSTITUTION_FUND', '事业基金',     '事业基金',     true,  false, false, 1),
    ('own_fund_category_welfare', 'EMPLOYEE_WELFARE_FUND',   '职工福利基金', '职工福利基金', false, true,  false, 2),
    ('own_fund_category_safety',  'SAFETY_PRODUCTION_FUND',  '安全生产基金', '安全生产基金', false, false, true,  3)
) AS t(fund_category_id, code, raw_value, label, is_career, is_welfare, is_safety, sort_order)

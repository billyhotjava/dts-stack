{{ config(materialized='table', tags=['finance', 'dim', 'dwd', 'prefix']) }}

SELECT prefix, subject_category_code
FROM (
  VALUES
    ('1122', 'RECEIVABLE_LOAN'),
    ('2211', 'PAYROLL_PAYABLE')
) AS t(prefix, subject_category_code)

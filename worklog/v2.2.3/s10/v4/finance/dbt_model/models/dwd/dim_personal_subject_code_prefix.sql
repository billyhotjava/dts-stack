{{ config(materialized='table', tags=['finance', 'dim', 'dwd', 'prefix']) }}

SELECT prefix, subject_category_code
FROM (
  VALUES
    ('1122', '其他应收-借款'),
    ('2211', '应付职工薪酬')
) AS t(prefix, subject_category_code)

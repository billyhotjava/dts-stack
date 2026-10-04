{{ config(materialized='table', tags=['finance', 'dim', 'dwd', 'prefix']) }}

SELECT prefix, expense_category_code
FROM (
  VALUES
    ('5001', 'MATERIAL_EQUIPMENT'),
    ('5101', 'OUTSOURCING_SERVICE'),
    ('5201', 'DEPRECIATION'),
    ('5301', 'TESTING'),
    ('5401', 'DESIGN_CONSULTING'),
    ('5501', 'RENTAL'),
    ('5601', 'TRAINING')
) AS t(prefix, expense_category_code)

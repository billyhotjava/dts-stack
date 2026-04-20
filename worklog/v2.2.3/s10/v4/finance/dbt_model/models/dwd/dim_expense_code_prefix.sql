{{ config(materialized='table', tags=['finance', 'dim', 'dwd', 'prefix']) }}

SELECT prefix, expense_category_code
FROM (
  VALUES
    ('5001', '原材料/设备'),
    ('5101', '外协/服务'),
    ('5201', '折旧'),
    ('5301', '检测试验'),
    ('5401', '设计咨询'),
    ('5501', '租赁'),
    ('5601', '培训')
) AS t(prefix, expense_category_code)

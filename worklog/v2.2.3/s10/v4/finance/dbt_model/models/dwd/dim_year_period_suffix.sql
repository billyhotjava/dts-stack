{{ config(materialized='table', tags=['finance', 'dim', 'dwd', 'suffix']) }}

SELECT suffix, period_type_code, sort_order
FROM (
  VALUES
    ('年初',       'opening',  1),
    ('预计增加',   'increase', 2),
    ('预计使用',   'usage',    3),
    ('余额',       'balance',  4)
) AS t(suffix, period_type_code, sort_order)

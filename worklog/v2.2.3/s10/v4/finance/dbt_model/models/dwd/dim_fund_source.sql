{{ config(materialized='table', tags=['finance', 'dim', 'dwd']) }}

SELECT fund_source_id, code, raw_value, label, is_opening, is_increase, is_usage, sort_order
FROM (
  VALUES
    ('own_fund_source_opening',  'OPENING',          '年初',     '年初',     true,  false, false, 1),
    ('own_fund_source_increase', 'PLANNED_INCREASE', '预计增加', '预计增加', false, true,  false, 2),
    ('own_fund_source_usage',    'PLANNED_USAGE',    '预计使用', '预计使用', false, false, true,  3)
) AS t(fund_source_id, code, raw_value, label, is_opening, is_increase, is_usage, sort_order)

{{ config(materialized='table', tags=['finance', 'dim', 'dwd']) }}

SELECT period_type_id, code, label, sort_order
FROM (
  VALUES
    ('own_fund_period_opening', 'opening', '年初', 1),
    ('own_fund_period_increase', 'increase', '预计增加', 2),
    ('own_fund_period_usage', 'usage', '预计使用', 3),
    ('own_fund_period_balance', 'balance', '余额', 4)
) AS t(period_type_id, code, label, sort_order)

{{ config(materialized='table', tags=['finance', 'dim', 'dwd']) }}

SELECT balance_direction_id, code, label, sort_order
FROM (
  VALUES
    ('balance_direction_debit', 'debit', '借方', 1),
    ('balance_direction_credit', 'credit', '贷方', 2),
    ('balance_direction_zero', 'zero', '零余额', 3)
) AS t(balance_direction_id, code, label, sort_order)

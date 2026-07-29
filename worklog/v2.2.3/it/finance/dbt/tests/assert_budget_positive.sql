SELECT *
FROM {{ ref('it_fin_demo_dwd_fct_budget_snapshot') }}
WHERE budget_amount <= 0

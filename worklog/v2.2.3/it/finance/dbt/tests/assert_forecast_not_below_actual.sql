SELECT *
FROM {{ ref('it_fin_demo_dwd_fct_budget_snapshot') }}
WHERE forecast_final_amount < actual_amount

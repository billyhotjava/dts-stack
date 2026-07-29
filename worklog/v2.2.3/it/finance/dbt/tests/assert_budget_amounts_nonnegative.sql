SELECT *
FROM {{ ref('it_fin_demo_dwd_fct_budget_snapshot') }}
WHERE budget_amount < 0
   OR committed_amount < 0
   OR actual_amount < 0
   OR payable_amount < 0
   OR forecast_final_amount < 0

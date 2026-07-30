SELECT *
FROM {{ ref('it_demo_dwd_fct_task_snapshot') }}
WHERE plan_cost_amount IS NULL
   OR actual_cost_amount IS NULL
   OR plan_cost_amount < 0
   OR actual_cost_amount < 0

SELECT *
FROM {{ ref('it_demo_dwd_fct_task_snapshot') }}
WHERE plan_start_date IS NULL
   OR plan_end_date IS NULL
   OR plan_start_date > plan_end_date

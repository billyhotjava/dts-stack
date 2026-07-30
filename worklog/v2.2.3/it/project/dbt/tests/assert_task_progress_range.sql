SELECT *
FROM {{ ref('it_demo_dwd_fct_task_snapshot') }}
WHERE progress_pct IS NULL
   OR progress_pct < 0
   OR progress_pct > 100

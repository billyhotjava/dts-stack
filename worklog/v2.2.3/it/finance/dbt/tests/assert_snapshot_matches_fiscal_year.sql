SELECT *
FROM {{ ref('it_fin_demo_dwd_fct_budget_snapshot') }}
WHERE extract(year FROM snapshot_date)::integer <> fiscal_year

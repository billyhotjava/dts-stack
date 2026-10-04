{{ config(materialized='table', tags=['it-fin-demo', 'ads', 'application']) }}

SELECT
    cost_center_budget_id AS finance_overview_id,
    snapshot_date,
    fiscal_year,
    cost_center_code,
    cost_center_name,
    finance_manager_name,
    budget_health_code,
    budget_amount,
    committed_amount,
    actual_amount,
    payable_amount,
    occupied_amount,
    forecast_final_amount,
    remaining_available_amount,
    overrun_amount,
    forecast_variance_amount,
    execution_rate,
    occupation_rate,
    payable_ratio
FROM {{ ref('it_fin_demo_dws_cost_center_budget') }}

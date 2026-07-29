{{ config(materialized='table', tags=['it-fin-demo', 'dws', 'summary']) }}

WITH aggregated AS (
    SELECT
        fact.snapshot_date,
        fact.fiscal_year,
        fact.cost_center_code,
        center.cost_center_name,
        center.manager_name AS finance_manager_name,
        sum(fact.budget_amount)::numeric(18,2) AS budget_amount,
        sum(fact.committed_amount)::numeric(18,2) AS committed_amount,
        sum(fact.actual_amount)::numeric(18,2) AS actual_amount,
        sum(fact.payable_amount)::numeric(18,2) AS payable_amount,
        sum(fact.occupied_amount)::numeric(18,2) AS occupied_amount,
        sum(fact.forecast_final_amount)::numeric(18,2) AS forecast_final_amount,
        sum(fact.remaining_available_amount)::numeric(18,2) AS remaining_available_amount,
        sum(fact.overrun_amount)::numeric(18,2) AS overrun_amount
    FROM {{ ref('it_fin_demo_dwd_fct_budget_snapshot') }} AS fact
    LEFT JOIN {{ ref('it_fin_demo_dwd_dim_cost_center') }} AS center
        ON center.cost_center_code = fact.cost_center_code
    GROUP BY
        fact.snapshot_date,
        fact.fiscal_year,
        fact.cost_center_code,
        center.cost_center_name,
        center.manager_name
),
calculated AS (
    SELECT
        aggregated.*,
        round(
            actual_amount / nullif(budget_amount, 0),
            4
        )::numeric(7,4) AS execution_rate,
        round(
            occupied_amount / nullif(budget_amount, 0),
            4
        )::numeric(7,4) AS occupation_rate,
        round(
            payable_amount / nullif(actual_amount, 0),
            4
        )::numeric(7,4) AS payable_ratio,
        (budget_amount - forecast_final_amount)::numeric(18,2) AS forecast_variance_amount
    FROM aggregated
)

SELECT
    md5(cost_center_code || '|' || snapshot_date::text) AS cost_center_budget_id,
    snapshot_date,
    fiscal_year,
    cost_center_code,
    cost_center_name,
    finance_manager_name,
    CASE
        WHEN forecast_final_amount > budget_amount THEN 'BH-RED'
        WHEN occupation_rate >= 0.85 OR payable_ratio >= 0.30 THEN 'BH-AMBER'
        ELSE 'BH-GREEN'
    END AS budget_health_code,
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
FROM calculated

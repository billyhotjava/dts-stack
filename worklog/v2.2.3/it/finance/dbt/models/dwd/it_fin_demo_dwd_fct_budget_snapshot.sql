{{ config(materialized='table', tags=['it-fin-demo', 'dwd', 'fact']) }}

WITH typed AS (
    SELECT
        cast(budget_line_code AS varchar(32)) AS budget_line_code,
        cast(snapshot_date AS date) AS snapshot_date,
        cast(fiscal_year AS integer) AS fiscal_year,
        cast(cost_center_code AS varchar(32)) AS cost_center_code,
        cast(account_code AS varchar(32)) AS account_code,
        cast(budget_amount AS numeric(18,2)) AS budget_amount,
        cast(committed_amount AS numeric(18,2)) AS committed_amount,
        cast(actual_amount AS numeric(18,2)) AS actual_amount,
        cast(payable_amount AS numeric(18,2)) AS payable_amount,
        cast(forecast_final_amount AS numeric(18,2)) AS forecast_final_amount,
        cast(updated_at AS timestamptz) AS source_updated_at,
        cast(source_batch_id AS varchar(64)) AS source_batch_id
    FROM {{ source('it_fin_demo_ods', 'budget_snapshot') }}
)

SELECT
    md5(budget_line_code || '|' || snapshot_date::text) AS budget_snapshot_id,
    budget_line_code,
    snapshot_date,
    fiscal_year,
    cost_center_code,
    account_code,
    budget_amount,
    committed_amount,
    actual_amount,
    payable_amount,
    (actual_amount + committed_amount)::numeric(18,2) AS occupied_amount,
    forecast_final_amount,
    (budget_amount - actual_amount - committed_amount)::numeric(18,2) AS remaining_available_amount,
    greatest(forecast_final_amount - budget_amount, 0)::numeric(18,2) AS overrun_amount,
    source_updated_at,
    source_batch_id
FROM typed

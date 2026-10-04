\set ON_ERROR_STOP on

BEGIN;

UPDATE it_fin_demo_src.budget_account
SET
    account_category_raw = '临时类',
    control_type_raw = '未知',
    updated_at = '2026-07-29 10:00:00+08',
    source_batch_id = 'FIN-DIRTY-20260729'
WHERE account_code = 'BA-SERVICE';

INSERT INTO it_fin_demo_src.budget_execution_snapshot (
    budget_line_code,
    snapshot_date,
    fiscal_year,
    cost_center_code,
    account_code,
    budget_amount,
    committed_amount,
    actual_amount,
    payable_amount,
    forecast_final_amount,
    updated_at,
    source_batch_id
)
VALUES (
    'BL-DIRTY-001',
    '2026-07-28',
    2025,
    'CC-NOT-FOUND',
    'BA-SERVICE',
    0.00,
    -10.00,
    100.00,
    500.00,
    50.00,
    '2026-07-29 10:05:00+08',
    'FIN-DIRTY-20260729'
)
ON CONFLICT (budget_line_code, snapshot_date) DO UPDATE
SET
    fiscal_year = EXCLUDED.fiscal_year,
    cost_center_code = EXCLUDED.cost_center_code,
    account_code = EXCLUDED.account_code,
    budget_amount = EXCLUDED.budget_amount,
    committed_amount = EXCLUDED.committed_amount,
    actual_amount = EXCLUDED.actual_amount,
    payable_amount = EXCLUDED.payable_amount,
    forecast_final_amount = EXCLUDED.forecast_final_amount,
    updated_at = EXCLUDED.updated_at,
    source_batch_id = EXCLUDED.source_batch_id;

COMMIT;

SELECT 'it_fin_demo_src.cost_center' AS source_table, count(*) AS row_count
FROM it_fin_demo_src.cost_center
UNION ALL
SELECT 'it_fin_demo_src.budget_account', count(*)
FROM it_fin_demo_src.budget_account
UNION ALL
SELECT 'it_fin_demo_src.budget_execution_snapshot', count(*)
FROM it_fin_demo_src.budget_execution_snapshot
ORDER BY source_table;

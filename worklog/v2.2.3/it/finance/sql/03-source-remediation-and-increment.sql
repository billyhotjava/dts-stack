\set ON_ERROR_STOP on

BEGIN;

DELETE FROM it_fin_demo_src.budget_execution_snapshot
WHERE budget_line_code = 'BL-DIRTY-001'
  AND snapshot_date = '2026-07-28';

UPDATE it_fin_demo_src.budget_account
SET
    account_category_raw = '服务费',
    control_type_raw = '软管控',
    updated_at = '2026-08-04 08:35:00+08',
    source_batch_id = 'FIN-REPAIR-20260804'
WHERE account_code = 'BA-SERVICE';

UPDATE it_fin_demo_src.cost_center
SET
    cost_center_name = '研发与创新费用中心',
    updated_at = '2026-08-04 08:30:00+08',
    source_batch_id = 'FIN-CHANGE-20260804'
WHERE cost_center_code = 'CC-RD';

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
VALUES
    (
        'BL-RD-PEOPLE', '2026-08-04', 2026, 'CC-RD', 'BA-PEOPLE',
        800000.00, 50000.00, 480000.00, 45000.00, 790000.00,
        '2026-08-04 18:00:00+08', 'FIN-SNAP-20260804'
    ),
    (
        'BL-RD-EQUIP', '2026-08-04', 2026, 'CC-RD', 'BA-EQUIP',
        500000.00, 200000.00, 320000.00, 55000.00, 500000.00,
        '2026-08-04 18:00:00+08', 'FIN-SNAP-20260804'
    ),
    (
        'BL-QA-PEOPLE', '2026-08-04', 2026, 'CC-QA', 'BA-PEOPLE',
        400000.00, 30000.00, 250000.00, 90000.00, 390000.00,
        '2026-08-04 18:00:00+08', 'FIN-SNAP-20260804'
    ),
    (
        'BL-QA-SERVICE', '2026-08-04', 2026, 'CC-QA', 'BA-SERVICE',
        300000.00, 80000.00, 170000.00, 60000.00, 300000.00,
        '2026-08-04 18:00:00+08', 'FIN-SNAP-20260804'
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

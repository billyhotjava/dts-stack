\set ON_ERROR_STOP on

BEGIN;

CREATE SCHEMA IF NOT EXISTS it_fin_demo_src;

DROP TABLE IF EXISTS it_fin_demo_src.budget_execution_snapshot;
DROP TABLE IF EXISTS it_fin_demo_src.budget_account;
DROP TABLE IF EXISTS it_fin_demo_src.cost_center;

CREATE TABLE it_fin_demo_src.cost_center (
    cost_center_code        varchar(32)   NOT NULL,
    cost_center_name        varchar(100)  NOT NULL,
    parent_cost_center_code varchar(32),
    manager_name            varchar(100)  NOT NULL,
    record_status_code      varchar(32)   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    source_batch_id         varchar(64)   NOT NULL,
    CONSTRAINT pk_it_fin_demo_cost_center
        PRIMARY KEY (cost_center_code),
    CONSTRAINT ck_it_fin_demo_cost_center_code_ascii
        CHECK (cost_center_code ~ '^[A-Z][A-Z0-9_-]{2,31}$')
);

CREATE INDEX idx_it_fin_demo_cost_center_parent
    ON it_fin_demo_src.cost_center (parent_cost_center_code);

CREATE INDEX idx_it_fin_demo_cost_center_updated
    ON it_fin_demo_src.cost_center (updated_at);

CREATE TABLE it_fin_demo_src.budget_account (
    account_code         varchar(32)   NOT NULL,
    account_name         varchar(100)  NOT NULL,
    account_category_raw varchar(32),
    control_type_raw     varchar(32),
    record_status_code   varchar(32)   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    source_batch_id      varchar(64)   NOT NULL,
    CONSTRAINT pk_it_fin_demo_budget_account
        PRIMARY KEY (account_code),
    CONSTRAINT ck_it_fin_demo_account_code_ascii
        CHECK (account_code ~ '^[A-Z][A-Z0-9_-]{2,31}$')
);

CREATE INDEX idx_it_fin_demo_budget_account_updated
    ON it_fin_demo_src.budget_account (updated_at);

CREATE TABLE it_fin_demo_src.budget_execution_snapshot (
    budget_line_code     varchar(32)   NOT NULL,
    snapshot_date        date          NOT NULL,
    fiscal_year          integer       NOT NULL,
    cost_center_code     varchar(32)   NOT NULL,
    account_code         varchar(32)   NOT NULL,
    budget_amount        numeric(18,2) NOT NULL,
    committed_amount     numeric(18,2) NOT NULL,
    actual_amount        numeric(18,2) NOT NULL,
    payable_amount       numeric(18,2) NOT NULL,
    forecast_final_amount numeric(18,2) NOT NULL,
    updated_at           timestamptz   NOT NULL,
    source_batch_id      varchar(64)   NOT NULL,
    CONSTRAINT pk_it_fin_demo_budget_snapshot
        PRIMARY KEY (budget_line_code, snapshot_date),
    CONSTRAINT ck_it_fin_demo_budget_line_code_ascii
        CHECK (budget_line_code ~ '^[A-Z][A-Z0-9_-]{2,31}$')
);

CREATE INDEX idx_it_fin_demo_budget_snapshot_center_date
    ON it_fin_demo_src.budget_execution_snapshot (
        cost_center_code,
        snapshot_date
    );

CREATE INDEX idx_it_fin_demo_budget_snapshot_account_date
    ON it_fin_demo_src.budget_execution_snapshot (
        account_code,
        snapshot_date
    );

CREATE INDEX idx_it_fin_demo_budget_snapshot_updated
    ON it_fin_demo_src.budget_execution_snapshot (updated_at);

COMMENT ON TABLE it_fin_demo_src.cost_center IS
    'Synthetic finance cost-center master data for the isolated DTS demo';
COMMENT ON TABLE it_fin_demo_src.budget_account IS
    'Synthetic finance budget-account master data for the isolated DTS demo';
COMMENT ON TABLE it_fin_demo_src.budget_execution_snapshot IS
    'Synthetic periodic budget execution snapshots; semantic constraints are validated by DTS';

INSERT INTO it_fin_demo_src.cost_center (
    cost_center_code,
    cost_center_name,
    parent_cost_center_code,
    manager_name,
    record_status_code,
    updated_at,
    source_batch_id
)
VALUES
    (
        'CC-HQ', '总部费用中心', NULL, '财务负责人甲',
        'RS-ACTIVE', '2026-07-28 08:30:00+08', 'FIN-BASE-20260728'
    ),
    (
        'CC-RD', '研发费用中心', 'CC-HQ', '财务负责人乙',
        'RS-ACTIVE', '2026-07-28 08:30:00+08', 'FIN-BASE-20260728'
    ),
    (
        'CC-QA', '质量费用中心', 'CC-HQ', '财务负责人丙',
        'RS-ACTIVE', '2026-07-28 08:30:00+08', 'FIN-BASE-20260728'
    )
ON CONFLICT (cost_center_code) DO UPDATE
SET
    cost_center_name = EXCLUDED.cost_center_name,
    parent_cost_center_code = EXCLUDED.parent_cost_center_code,
    manager_name = EXCLUDED.manager_name,
    record_status_code = EXCLUDED.record_status_code,
    updated_at = EXCLUDED.updated_at,
    source_batch_id = EXCLUDED.source_batch_id;

INSERT INTO it_fin_demo_src.budget_account (
    account_code,
    account_name,
    account_category_raw,
    control_type_raw,
    record_status_code,
    updated_at,
    source_batch_id
)
VALUES
    (
        'BA-PEOPLE', '人员费用', '人员费', '硬管控',
        'RS-ACTIVE', '2026-07-28 08:35:00+08', 'FIN-BASE-20260728'
    ),
    (
        'BA-EQUIP', '设备费用', '设备', 'HARD',
        'RS-ACTIVE', '2026-07-28 08:35:00+08', 'FIN-BASE-20260728'
    ),
    (
        'BA-MATERIAL', '材料费用', '材料费', '软管控',
        'RS-ACTIVE', '2026-07-28 08:35:00+08', 'FIN-BASE-20260728'
    ),
    (
        'BA-SERVICE', '外部服务费用', '服务', 'SOFT',
        'RS-ACTIVE', '2026-07-28 08:35:00+08', 'FIN-BASE-20260728'
    )
ON CONFLICT (account_code) DO UPDATE
SET
    account_name = EXCLUDED.account_name,
    account_category_raw = EXCLUDED.account_category_raw,
    control_type_raw = EXCLUDED.control_type_raw,
    record_status_code = EXCLUDED.record_status_code,
    updated_at = EXCLUDED.updated_at,
    source_batch_id = EXCLUDED.source_batch_id;

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
        'BL-RD-PEOPLE', '2026-06-30', 2026, 'CC-RD', 'BA-PEOPLE',
        800000.00, 70000.00, 320000.00, 50000.00, 790000.00,
        '2026-06-30 18:00:00+08', 'FIN-SNAP-20260630'
    ),
    (
        'BL-RD-EQUIP', '2026-06-30', 2026, 'CC-RD', 'BA-EQUIP',
        500000.00, 180000.00, 140000.00, 20000.00, 520000.00,
        '2026-06-30 18:00:00+08', 'FIN-SNAP-20260630'
    ),
    (
        'BL-QA-PEOPLE', '2026-06-30', 2026, 'CC-QA', 'BA-PEOPLE',
        400000.00, 50000.00, 160000.00, 30000.00, 390000.00,
        '2026-06-30 18:00:00+08', 'FIN-SNAP-20260630'
    ),
    (
        'BL-QA-SERVICE', '2026-06-30', 2026, 'CC-QA', 'BA-SERVICE',
        300000.00, 80000.00, 100000.00, 45000.00, 310000.00,
        '2026-06-30 18:00:00+08', 'FIN-SNAP-20260630'
    ),
    (
        'BL-RD-PEOPLE', '2026-07-28', 2026, 'CC-RD', 'BA-PEOPLE',
        800000.00, 60000.00, 400000.00, 40000.00, 800000.00,
        '2026-07-28 18:00:00+08', 'FIN-SNAP-20260728'
    ),
    (
        'BL-RD-EQUIP', '2026-07-28', 2026, 'CC-RD', 'BA-EQUIP',
        500000.00, 150000.00, 260000.00, 50000.00, 560000.00,
        '2026-07-28 18:00:00+08', 'FIN-SNAP-20260728'
    ),
    (
        'BL-QA-PEOPLE', '2026-07-28', 2026, 'CC-QA', 'BA-PEOPLE',
        400000.00, 40000.00, 210000.00, 80000.00, 395000.00,
        '2026-07-28 18:00:00+08', 'FIN-SNAP-20260728'
    ),
    (
        'BL-QA-SERVICE', '2026-07-28', 2026, 'CC-QA', 'BA-SERVICE',
        300000.00, 90000.00, 180000.00, 60000.00, 320000.00,
        '2026-07-28 18:00:00+08', 'FIN-SNAP-20260728'
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

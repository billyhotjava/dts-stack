\set ON_ERROR_STOP on

BEGIN;

CREATE SCHEMA IF NOT EXISTS it_demo_src;

COMMENT ON SCHEMA it_demo_src IS
    'Isolated synthetic source data for the DTS v2.2.3 governance demo';

CREATE TABLE IF NOT EXISTS it_demo_src.org (
    org_code           varchar(32)  NOT NULL,
    org_name           varchar(100) NOT NULL,
    parent_org_code    varchar(32),
    org_level_code     varchar(32)  NOT NULL,
    record_status_code varchar(32)  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    source_batch_id    varchar(64)  NOT NULL,
    CONSTRAINT pk_it_demo_org PRIMARY KEY (org_code),
    CONSTRAINT ck_it_demo_org_code_ascii
        CHECK (org_code ~ '^[A-Z][A-Z0-9_-]{2,31}$')
);

CREATE INDEX IF NOT EXISTS idx_it_demo_org_parent
    ON it_demo_src.org (parent_org_code);

CREATE INDEX IF NOT EXISTS idx_it_demo_org_updated
    ON it_demo_src.org (updated_at);

COMMENT ON TABLE it_demo_src.org IS
    'Synthetic organization master data; no customer data';

CREATE TABLE IF NOT EXISTS it_demo_src.project (
    project_code       varchar(32)   NOT NULL,
    project_name       varchar(200)  NOT NULL,
    owner_org_code     varchar(32)   NOT NULL,
    manager_name       varchar(100)  NOT NULL,
    project_status_code varchar(32)  NOT NULL,
    plan_start_date    date          NOT NULL,
    plan_end_date      date          NOT NULL,
    budget_amount      numeric(18,2) NOT NULL,
    updated_at         timestamptz   NOT NULL,
    source_batch_id    varchar(64)   NOT NULL,
    CONSTRAINT pk_it_demo_project PRIMARY KEY (project_code),
    CONSTRAINT ck_it_demo_project_code_ascii
        CHECK (project_code ~ '^[A-Z][A-Z0-9_-]{2,31}$'),
    CONSTRAINT ck_it_demo_project_budget_nonnegative
        CHECK (budget_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_it_demo_project_owner_org
    ON it_demo_src.project (owner_org_code);

CREATE INDEX IF NOT EXISTS idx_it_demo_project_updated
    ON it_demo_src.project (updated_at);

COMMENT ON TABLE it_demo_src.project IS
    'Synthetic project master data; organization reference is intentionally validated by DTS quality rules';

CREATE TABLE IF NOT EXISTS it_demo_src.task_snapshot (
    task_code          varchar(32)   NOT NULL,
    snapshot_date      date          NOT NULL,
    project_code       varchar(32)   NOT NULL,
    task_name          varchar(200)  NOT NULL,
    owner_name         varchar(100)  NOT NULL,
    task_status_raw    varchar(32),
    risk_level_raw     varchar(32),
    plan_start_date    date          NOT NULL,
    plan_end_date      date          NOT NULL,
    actual_finish_date date,
    progress_pct       numeric(5,2),
    plan_cost          numeric(18,2),
    actual_cost        numeric(18,2),
    updated_at         timestamptz   NOT NULL,
    source_batch_id    varchar(64)   NOT NULL,
    CONSTRAINT pk_it_demo_task_snapshot
        PRIMARY KEY (task_code, snapshot_date),
    CONSTRAINT ck_it_demo_task_code_ascii
        CHECK (task_code ~ '^[A-Z][A-Z0-9_-]{2,31}$')
);

CREATE INDEX IF NOT EXISTS idx_it_demo_task_project_snapshot
    ON it_demo_src.task_snapshot (project_code, snapshot_date);

CREATE INDEX IF NOT EXISTS idx_it_demo_task_updated
    ON it_demo_src.task_snapshot (updated_at);

COMMENT ON TABLE it_demo_src.task_snapshot IS
    'Synthetic periodic task snapshots; semantic constraints are intentionally validated by DTS';

INSERT INTO it_demo_src.org (
    org_code,
    org_name,
    parent_org_code,
    org_level_code,
    record_status_code,
    updated_at,
    source_batch_id
)
VALUES
    ('ORG-HQ', '研发总部', NULL, 'OL-HQ', 'RS-ACTIVE', '2026-07-21 09:00:00+08', 'BASE-20260721'),
    ('ORG-RD', '研发中心', 'ORG-HQ', 'OL-DEPT', 'RS-ACTIVE', '2026-07-21 09:00:00+08', 'BASE-20260721'),
    ('ORG-QA', '质量中心', 'ORG-HQ', 'OL-DEPT', 'RS-ACTIVE', '2026-07-21 09:00:00+08', 'BASE-20260721')
ON CONFLICT (org_code) DO UPDATE
SET
    org_name = EXCLUDED.org_name,
    parent_org_code = EXCLUDED.parent_org_code,
    org_level_code = EXCLUDED.org_level_code,
    record_status_code = EXCLUDED.record_status_code,
    updated_at = EXCLUDED.updated_at,
    source_batch_id = EXCLUDED.source_batch_id;

INSERT INTO it_demo_src.project (
    project_code,
    project_name,
    owner_org_code,
    manager_name,
    project_status_code,
    plan_start_date,
    plan_end_date,
    budget_amount,
    updated_at,
    source_batch_id
)
VALUES
    (
        'PRJ-A',
        '火星车控制单元研发',
        'ORG-RD',
        '张工',
        'PJ-ACTIVE',
        '2026-07-01',
        '2026-09-30',
        1000000.00,
        '2026-07-21 09:05:00+08',
        'BASE-20260721'
    ),
    (
        'PRJ-B',
        '星载测试平台升级',
        'ORG-QA',
        '李工',
        'PJ-ACTIVE',
        '2026-07-01',
        '2026-10-31',
        500000.00,
        '2026-07-21 09:05:00+08',
        'BASE-20260721'
    )
ON CONFLICT (project_code) DO UPDATE
SET
    project_name = EXCLUDED.project_name,
    owner_org_code = EXCLUDED.owner_org_code,
    manager_name = EXCLUDED.manager_name,
    project_status_code = EXCLUDED.project_status_code,
    plan_start_date = EXCLUDED.plan_start_date,
    plan_end_date = EXCLUDED.plan_end_date,
    budget_amount = EXCLUDED.budget_amount,
    updated_at = EXCLUDED.updated_at,
    source_batch_id = EXCLUDED.source_batch_id;

INSERT INTO it_demo_src.task_snapshot (
    task_code,
    snapshot_date,
    project_code,
    task_name,
    owner_name,
    task_status_raw,
    risk_level_raw,
    plan_start_date,
    plan_end_date,
    actual_finish_date,
    progress_pct,
    plan_cost,
    actual_cost,
    updated_at,
    source_batch_id
)
VALUES
    (
        'TASK-A-01', '2026-07-21', 'PRJ-A', '控制算法设计', '王工',
        '已完成', '低', '2026-07-01', '2026-07-20', '2026-07-18',
        100.00, 120000.00, 110000.00, '2026-07-21 09:10:00+08', 'SNAP-20260721'
    ),
    (
        'TASK-A-02', '2026-07-21', 'PRJ-A', '控制器样机研制', '赵工',
        '进行中', '高风险', '2026-07-10', '2026-08-15', NULL,
        45.00, 300000.00, 120000.00, '2026-07-21 09:10:00+08', 'SNAP-20260721'
    ),
    (
        'TASK-B-01', '2026-07-21', 'PRJ-B', '测试环境搭建', '陈工',
        '进行中', '中', '2026-07-01', '2026-07-25', NULL,
        55.00, 180000.00, 90000.00, '2026-07-21 09:10:00+08', 'SNAP-20260721'
    ),
    (
        'TASK-B-02', '2026-07-21', 'PRJ-B', '自动化脚本升级', '周工',
        '未开始', '低风险', '2026-07-20', '2026-08-31', NULL,
        0.00, 120000.00, 0.00, '2026-07-21 09:10:00+08', 'SNAP-20260721'
    ),
    (
        'TASK-A-01', '2026-07-28', 'PRJ-A', '控制算法设计', '王工',
        '完成', 'L', '2026-07-01', '2026-07-20', '2026-07-18',
        100.00, 120000.00, 110000.00, '2026-07-28 09:10:00+08', 'SNAP-20260728'
    ),
    (
        'TASK-A-02', '2026-07-28', 'PRJ-A', '控制器样机研制', '赵工',
        '执行中', 'H', '2026-07-10', '2026-08-15', NULL,
        65.00, 300000.00, 190000.00, '2026-07-28 09:10:00+08', 'SNAP-20260728'
    ),
    (
        'TASK-B-01', '2026-07-28', 'PRJ-B', '测试环境搭建', '陈工',
        '已延期', '高', '2026-07-01', '2026-07-25', NULL,
        70.00, 180000.00, 145000.00, '2026-07-28 09:10:00+08', 'SNAP-20260728'
    ),
    (
        'TASK-B-02', '2026-07-28', 'PRJ-B', '自动化脚本升级', '周工',
        '执行中', '中风险', '2026-07-20', '2026-08-31', NULL,
        20.00, 120000.00, 25000.00, '2026-07-28 09:10:00+08', 'SNAP-20260728'
    )
ON CONFLICT (task_code, snapshot_date) DO UPDATE
SET
    project_code = EXCLUDED.project_code,
    task_name = EXCLUDED.task_name,
    owner_name = EXCLUDED.owner_name,
    task_status_raw = EXCLUDED.task_status_raw,
    risk_level_raw = EXCLUDED.risk_level_raw,
    plan_start_date = EXCLUDED.plan_start_date,
    plan_end_date = EXCLUDED.plan_end_date,
    actual_finish_date = EXCLUDED.actual_finish_date,
    progress_pct = EXCLUDED.progress_pct,
    plan_cost = EXCLUDED.plan_cost,
    actual_cost = EXCLUDED.actual_cost,
    updated_at = EXCLUDED.updated_at,
    source_batch_id = EXCLUDED.source_batch_id;

COMMIT;

SELECT 'it_demo_src.org' AS source_table, count(*) AS row_count
FROM it_demo_src.org
UNION ALL
SELECT 'it_demo_src.project', count(*)
FROM it_demo_src.project
UNION ALL
SELECT 'it_demo_src.task_snapshot', count(*)
FROM it_demo_src.task_snapshot
ORDER BY source_table;

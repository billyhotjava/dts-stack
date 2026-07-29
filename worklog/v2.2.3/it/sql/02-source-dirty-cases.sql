\set ON_ERROR_STOP on

BEGIN;

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
VALUES (
    'PRJ-DIRTY',
    '质量阻断演示项目',
    'ORG-NOT-FOUND',
    '演示人员',
    'PJ-ACTIVE',
    '2026-07-01',
    '2026-09-30',
    10000.00,
    '2026-07-29 10:00:00+08',
    'DIRTY-20260729'
)
ON CONFLICT (project_code) DO UPDATE
SET
    owner_org_code = EXCLUDED.owner_org_code,
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
VALUES (
    'TASK-DIRTY-001',
    '2026-07-28',
    'PRJ-NOT-FOUND',
    '质量规则失败演示任务',
    '演示人员',
    '暂停中',
    '极高',
    '2026-08-20',
    '2026-07-20',
    NULL,
    130.00,
    50000.00,
    -100.00,
    '2026-07-29 10:05:00+08',
    'DIRTY-20260729'
)
ON CONFLICT (task_code, snapshot_date) DO UPDATE
SET
    project_code = EXCLUDED.project_code,
    task_status_raw = EXCLUDED.task_status_raw,
    risk_level_raw = EXCLUDED.risk_level_raw,
    plan_start_date = EXCLUDED.plan_start_date,
    plan_end_date = EXCLUDED.plan_end_date,
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

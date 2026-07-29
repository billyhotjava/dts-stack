\set ON_ERROR_STOP on

BEGIN;

DELETE FROM it_demo_src.task_snapshot
WHERE task_code = 'TASK-DIRTY-001'
  AND snapshot_date = '2026-07-28';

DELETE FROM it_demo_src.project
WHERE project_code = 'PRJ-DIRTY';

UPDATE it_demo_src.org
SET
    org_name = '研发与系统中心',
    updated_at = '2026-08-04 09:00:00+08',
    source_batch_id = 'CHANGE-20260804'
WHERE org_code = 'ORG-RD';

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
        'TASK-A-01', '2026-08-04', 'PRJ-A', '控制算法设计', '王工',
        '已完成', '低风险', '2026-07-01', '2026-07-20', '2026-07-18',
        100.00, 120000.00, 110000.00, '2026-08-04 09:10:00+08', 'SNAP-20260804'
    ),
    (
        'TASK-A-02', '2026-08-04', 'PRJ-A', '控制器样机研制', '赵工',
        '进行中', '高', '2026-07-10', '2026-08-15', NULL,
        80.00, 300000.00, 245000.00, '2026-08-04 09:10:00+08', 'SNAP-20260804'
    ),
    (
        'TASK-B-01', '2026-08-04', 'PRJ-B', '测试环境搭建', '陈工',
        '完成', '中', '2026-07-01', '2026-07-25', '2026-08-02',
        100.00, 180000.00, 175000.00, '2026-08-04 09:10:00+08', 'SNAP-20260804'
    ),
    (
        'TASK-B-02', '2026-08-04', 'PRJ-B', '自动化脚本升级', '周工',
        '执行中', 'M', '2026-07-20', '2026-08-31', NULL,
        55.00, 120000.00, 60000.00, '2026-08-04 09:10:00+08', 'SNAP-20260804'
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

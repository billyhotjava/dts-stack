{{ config(materialized='table', tags=['it-demo', 'dwd', 'fact']) }}

WITH task_clean AS (
    SELECT
        cast(task_code AS varchar(32)) AS task_code,
        cast(snapshot_date AS date) AS snapshot_date,
        cast(project_code AS varchar(32)) AS project_code,
        cast(task_name AS varchar(200)) AS task_name,
        cast(owner_name AS varchar(100)) AS owner_name,
        cast(task_status_raw AS varchar(32)) AS task_status_raw,
        cast(risk_level_raw AS varchar(32)) AS risk_level_raw,
        upper(
            btrim(
                replace(
                    replace(
                        replace(coalesce(cast(task_status_raw AS text), ''), chr(160), ''),
                        chr(65279),
                        ''
                    ),
                    chr(8203),
                    ''
                )
            )
        ) AS task_status_normalized,
        upper(
            btrim(
                replace(
                    replace(
                        replace(coalesce(cast(risk_level_raw AS text), ''), chr(160), ''),
                        chr(65279),
                        ''
                    ),
                    chr(8203),
                    ''
                )
            )
        ) AS risk_level_normalized,
        cast(plan_start_date AS date) AS plan_start_date,
        cast(plan_end_date AS date) AS plan_end_date,
        cast(actual_finish_date AS date) AS actual_finish_date,
        cast(progress_pct AS numeric(5,2)) AS progress_pct,
        cast(plan_cost AS numeric(18,2)) AS plan_cost_amount,
        cast(actual_cost AS numeric(18,2)) AS actual_cost_amount,
        cast(updated_at AS timestamptz) AS source_updated_at,
        cast(source_batch_id AS varchar(64)) AS source_batch_id
    FROM {{ source('it_demo_ods', 'task_snapshot') }}
),
mapped AS (
    SELECT
        task_clean.*,
        CASE
            WHEN task_status_normalized IN ('未开始', '待启动') THEN 'TS-NOT-STARTED'
            WHEN task_status_normalized IN ('进行中', '执行中') THEN 'TS-IN-PROGRESS'
            WHEN task_status_normalized IN ('已完成', '完成') THEN 'TS-DONE'
            WHEN task_status_normalized IN ('延期', '已延期') THEN 'TS-OVERDUE'
            ELSE 'TS-UNKNOWN'
        END AS task_status_code,
        CASE
            WHEN risk_level_normalized IN ('低', '低风险', 'L') THEN 'RL-LOW'
            WHEN risk_level_normalized IN ('中', '中风险', 'M') THEN 'RL-MID'
            WHEN risk_level_normalized IN ('高', '高风险', 'H') THEN 'RL-HIGH'
            ELSE 'RL-UNKNOWN'
        END AS risk_level_code
    FROM task_clean
),
joined AS (
    SELECT
        mapped.*,
        cast(project.owner_org_code AS varchar(32)) AS owner_org_code
    FROM mapped
    LEFT JOIN {{ source('it_demo_ods', 'project') }} AS project
        ON project.project_code = mapped.project_code
)

SELECT
    md5(task_code || '|' || snapshot_date::text) AS task_snapshot_id,
    task_code,
    snapshot_date,
    project_code,
    owner_org_code,
    task_name,
    owner_name,
    task_status_raw,
    risk_level_raw,
    task_status_code,
    risk_level_code,
    plan_start_date,
    plan_end_date,
    actual_finish_date,
    progress_pct,
    1::integer AS task_count,
    CASE WHEN task_status_code = 'TS-DONE' THEN 1 ELSE 0 END::integer AS completed_task_count,
    CASE
        WHEN task_status_code = 'TS-OVERDUE'
            OR (task_status_code <> 'TS-DONE' AND plan_end_date < snapshot_date)
        THEN 1
        ELSE 0
    END::integer AS overdue_task_count,
    CASE WHEN risk_level_code = 'RL-HIGH' THEN 1 ELSE 0 END::integer AS high_risk_task_count,
    plan_cost_amount,
    actual_cost_amount,
    (actual_cost_amount - plan_cost_amount)::numeric(18,2) AS cost_variance_amount,
    source_updated_at,
    source_batch_id
FROM joined

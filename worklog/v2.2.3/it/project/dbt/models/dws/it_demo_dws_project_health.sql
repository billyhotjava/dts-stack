{{ config(materialized='table', tags=['it-demo', 'dws', 'summary']) }}

WITH aggregated AS (
    SELECT
        fact.snapshot_date,
        fact.project_code,
        project.project_name,
        project.owner_org_code AS org_code,
        org.org_name,
        project.manager_name AS project_manager_name,
        sum(fact.task_count)::integer AS task_total,
        sum(fact.completed_task_count)::integer AS completed_task_count,
        sum(fact.overdue_task_count)::integer AS overdue_task_count,
        sum(fact.high_risk_task_count)::integer AS high_risk_task_count,
        round(avg(fact.progress_pct), 4)::numeric(7,4) AS avg_progress_pct,
        sum(fact.plan_cost_amount)::numeric(18,2) AS plan_cost_amount,
        sum(fact.actual_cost_amount)::numeric(18,2) AS actual_cost_amount
    FROM {{ ref('it_demo_dwd_fct_task_snapshot') }} AS fact
    LEFT JOIN {{ ref('it_demo_dwd_dim_project') }} AS project
        ON project.project_code = fact.project_code
    LEFT JOIN {{ ref('it_demo_dwd_dim_org') }} AS org
        ON org.org_code = project.owner_org_code
    GROUP BY
        fact.snapshot_date,
        fact.project_code,
        project.project_name,
        project.owner_org_code,
        org.org_name,
        project.manager_name
),
calculated AS (
    SELECT
        aggregated.*,
        round(
            completed_task_count::numeric / nullif(task_total, 0),
            4
        )::numeric(7,4) AS completion_rate,
        round(
            overdue_task_count::numeric / nullif(task_total, 0),
            4
        )::numeric(7,4) AS overdue_rate,
        (actual_cost_amount - plan_cost_amount)::numeric(18,2) AS cost_variance_amount
    FROM aggregated
)

SELECT
    md5(project_code || '|' || snapshot_date::text) AS project_health_id,
    snapshot_date,
    project_code,
    project_name,
    org_code,
    org_name,
    project_manager_name,
    CASE
        WHEN overdue_task_count > 0 OR high_risk_task_count > 0 THEN 'PH-RED'
        WHEN completion_rate < 0.50 THEN 'PH-AMBER'
        ELSE 'PH-GREEN'
    END AS health_status_code,
    task_total,
    completed_task_count,
    overdue_task_count,
    high_risk_task_count,
    avg_progress_pct,
    completion_rate,
    overdue_rate,
    plan_cost_amount,
    actual_cost_amount,
    cost_variance_amount
FROM calculated

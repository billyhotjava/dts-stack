{{ config(materialized='table', tags=['it-demo', 'ads', 'application']) }}

SELECT
    project_health_id AS project_overview_id,
    snapshot_date,
    project_code,
    project_name,
    org_code,
    org_name,
    project_manager_name,
    health_status_code,
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
FROM {{ ref('it_demo_dws_project_health') }}

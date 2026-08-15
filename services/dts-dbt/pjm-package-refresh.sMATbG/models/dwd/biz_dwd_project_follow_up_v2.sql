{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'progress-measure']) }}

WITH stg AS (
  SELECT *
  FROM {{ ref('stg_pm__progress_measure_v2') }}
  WHERE project_no IS NOT NULL
    AND node_task IS NOT NULL
    AND measure_title IS NOT NULL
    AND follow_up_date IS NOT NULL
), derived AS (
  SELECT
    s.*,
    COALESCE(s.last_update_time, current_date) AS state_as_of_date,
    (s.final_closure_date IS NOT NULL OR s.closure_status = '已闭环') AS is_closed
  FROM stg s
)

SELECT
  concat('project_follow_up:', md5(concat_ws(chr(31), d.project_no, d.node_task, d.measure_title, d.follow_up_date::text))) AS measure_id,
  concat('project_node_context:', md5(concat_ws(chr(31), d.project_no, d.node_task, COALESCE(d.plan_date::text, '')))) AS parent_context_id,
  d.source_row_id,
  d.source_table,
  d.source_system,
  d.imported_at AS source_imported_at,
  d.project_no,
  d.subsystem,
  d.node_task,
  d.completion_status,
  d.measure_category,
  d.measure_title,
  d.follow_up_person,
  d.main_recipient,
  d.cc_recipient,
  d.closure_status,
  d.closure_deliverable_type,
  d.closure_deliverable,
  d.risk_content,
  d.remark,
  d.filled_by,
  d.plan_date,
  d.follow_up_date,
  d.final_closure_date,
  d.last_update_time,
  d.plan_week,
  d.follow_up_week,
  d.final_closure_week,
  d.last_update_week,
  EXTRACT(YEAR FROM d.follow_up_date)::int AS follow_up_year,
  to_char(d.follow_up_date, 'YYYY-MM') AS follow_up_month,
  d.state_as_of_date,
  d.is_closed,
  CASE WHEN d.final_closure_date IS NOT NULL THEN GREATEST(0, d.final_closure_date - d.follow_up_date) END AS closure_days,
  CASE WHEN NOT d.is_closed THEN GREATEST(0, d.state_as_of_date - d.follow_up_date) ELSE 0 END AS pending_days,
  now() AS etl_time
FROM derived d

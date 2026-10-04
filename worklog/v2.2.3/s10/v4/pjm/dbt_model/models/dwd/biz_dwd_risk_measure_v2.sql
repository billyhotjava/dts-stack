{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'risk-measure']) }}

WITH stg AS (
  SELECT *
  FROM {{ ref('stg_pm__risk_measure_v2') }}
  WHERE project_no IS NOT NULL
    AND risk_name IS NOT NULL
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
  concat('risk_measure:', md5(concat_ws(chr(31), d.project_no, d.risk_name, d.measure_title, d.follow_up_date::text))) AS measure_id,
  concat('risk_context:', md5(concat_ws(chr(31), d.project_no, d.risk_name, COALESCE(d.risk_submit_date::text, '')))) AS parent_context_id,
  d.source_row_id,
  d.source_table,
  d.source_system,
  d.imported_at AS source_imported_at,
  d.project_no,
  d.risk_name,
  d.subsystem,
  d.belonging_unit,
  d.risk_description,
  d.risk_phase,
  d.risk_category,
  d.risk_level,
  d.impact_scope,
  d.response_measure,
  d.monthly_control_plan,
  d.weekly_release_plan,
  d.release_plan_synced,
  d.new_plan_count,
  d.progress_situation,
  d.response_owner,
  d.control_owner,
  d.dept,
  d.risk_status,
  d.project_manager,
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
  d.risk_submit_date,
  d.final_release_date,
  d.progress_stat_date,
  d.follow_up_date,
  d.final_closure_date,
  d.last_update_time,
  d.risk_submit_week,
  d.progress_stat_week,
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

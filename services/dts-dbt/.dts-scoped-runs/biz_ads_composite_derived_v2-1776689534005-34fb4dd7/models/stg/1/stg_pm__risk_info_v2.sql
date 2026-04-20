{{ config(materialized='view', tags=['project-management-v3', 'stg', 'risk']) }}

SELECT
  o.id AS source_row_id,
  'ods_risk_info_v2'::text AS source_table,
  COALESCE({{ nullif_placeholder("o.source_system") }}, 'excel') AS source_system,
  {{ nullif_placeholder("o.source_file") }} AS source_file,
  {{ nullif_placeholder("o.sheet_name") }} AS source_sheet_name,
  {{ nullif_placeholder("o.batch_id") }} AS source_batch_id,
  {{ parse_numeric_safe("o.row_num") }}::int AS source_row_num,
  o.import_time AS imported_at,

  {{ nullif_placeholder("o.project_no") }} AS project_no,
  {{ nullif_placeholder("o.risk_name") }} AS risk_name,
  {{ nullif_placeholder("o.subsystem") }} AS subsystem,
  {{ nullif_placeholder("o.belonging_unit") }} AS belonging_unit,
  {{ nullif_placeholder("o.risk_description") }} AS risk_description,
  {{ nullif_placeholder("o.risk_phase") }} AS risk_phase,
  {{ nullif_placeholder("o.impact_scope") }} AS impact_scope,
  {{ nullif_placeholder("o.response_measure") }} AS response_measure,
  {{ nullif_placeholder("o.monthly_control_plan") }} AS monthly_control_plan,
  {{ nullif_placeholder("o.weekly_release_plan") }} AS weekly_release_plan,
  {{ nullif_placeholder("o.progress_situation") }} AS progress_situation,
  {{ nullif_placeholder("o.response_owner") }} AS response_owner,
  {{ nullif_placeholder("o.control_owner") }} AS control_owner,
  {{ nullif_placeholder("o.dept") }} AS dept,
  {{ nullif_placeholder("o.remark") }} AS remark,
  {{ nullif_placeholder("o.filled_by") }} AS filled_by,

  {{ nullif_placeholder("o.risk_category") }} AS risk_category_raw,
  {{ nullif_placeholder("o.risk_level") }} AS risk_level_raw,
  {{ nullif_placeholder("o.release_plan_synced") }} AS release_plan_synced_raw,
  {{ nullif_placeholder("o.risk_status") }} AS risk_status_raw,
  {{ nullif_placeholder("o.risk_submit_time") }} AS risk_submit_time_raw,

  {{ parse_numeric_safe("o.new_plan_count") }}::int AS new_plan_count,
  {{ parse_numeric_safe("o.risk_submit_week") }}::int AS risk_submit_week,
  {{ parse_numeric_safe("o.final_release_week") }}::int AS final_release_week,
  {{ parse_numeric_safe("o.progress_stat_week") }}::int AS progress_stat_week,
  {{ parse_numeric_safe("o.risk_release_week") }}::int AS risk_release_week,
  {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week,

  {{ parse_date_safe("o.risk_submit_time") }} AS risk_submit_date,
  {{ parse_date_safe("o.final_release_time") }} AS final_release_date,
  {{ parse_date_safe("o.progress_stat_time") }} AS progress_stat_date,
  {{ parse_date_safe("o.risk_release_date") }} AS risk_release_date,
  {{ parse_date_safe("o.last_update_time") }} AS last_update_time
FROM {{ source('pm_ods_v2', 'risk_info_v2') }} o

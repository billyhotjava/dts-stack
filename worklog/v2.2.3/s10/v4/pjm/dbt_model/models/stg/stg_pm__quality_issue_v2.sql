{{ config(materialized='view', tags=['project-management-v3', 'stg', 'quality']) }}

SELECT
  o.id AS source_row_id,
  'ods_quality_issue_v2'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'excel') AS source_system,
  o._dts_import_time AS imported_at,

  {{ nullif_placeholder("o.project_no") }} AS project_no,
  {{ nullif_placeholder("o.subsystem") }} AS subsystem,
  {{ nullif_placeholder("o.issue_name") }} AS issue_name,
  {{ nullif_placeholder("o.dept") }} AS dept,
  {{ nullif_placeholder("o.team_leader") }} AS team_leader,
  {{ nullif_placeholder("o.dept_leader") }} AS dept_leader,
  {{ nullif_placeholder("o.issue_summary") }} AS issue_summary,
  {{ nullif_placeholder("o.zero_plan") }} AS zero_plan,
  {{ nullif_placeholder("o.current_progress") }} AS current_progress,
  {{ nullif_placeholder("o.project_manager") }} AS project_manager,
  {{ nullif_placeholder("o.filled_by") }} AS filled_by,

  {{ nullif_placeholder("o.issue_category") }} AS issue_category_raw,
  {{ nullif_placeholder("o.zero_plan_synced") }} AS zero_plan_synced_raw,
  {{ nullif_placeholder("o.status") }} AS status_raw,
  {{ nullif_placeholder("o.issue_date") }} AS issue_date_raw,

  {{ parse_numeric_safe("o.new_plan_count") }}::int AS new_plan_count,
  {{ parse_date_safe("o.issue_date") }} AS issue_date,
  {{ parse_date_safe("o.zero_complete_date") }} AS zero_complete_date,
  {{ parse_date_safe("o.last_update_time") }} AS last_update_time,
  {{ parse_numeric_safe("o.issue_week") }}::int AS issue_week,
  {{ parse_numeric_safe("o.zero_complete_week") }}::int AS zero_complete_week,
  {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week
FROM {{ source('pm_ods_v2', 'quality_issue_v2') }} o

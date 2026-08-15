{{ config(materialized='view', tags=['project-management-v3', 'stg', 'progress-measure']) }}

SELECT
  o.id::bigint AS source_row_id,
  'ods_progress_measure_v2'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'excel') AS source_system,
  o._dts_import_time AS imported_at,

  {{ nullif_placeholder("o.project_no") }} AS project_no,
  {{ nullif_placeholder("o.subsystem") }} AS subsystem,
  {{ nullif_placeholder("o.node_task") }} AS node_task,
  {{ nullif_placeholder("o.completion_status") }} AS completion_status,
  {{ nullif_placeholder("o.measure_category") }} AS measure_category,
  {{ nullif_placeholder("o.measure_title") }} AS measure_title,
  {{ nullif_placeholder("o.follow_up_person") }} AS follow_up_person,
  {{ nullif_placeholder("o.main_recipient") }} AS main_recipient,
  {{ nullif_placeholder("o.cc_recipient") }} AS cc_recipient,
  {{ nullif_placeholder("o.closure_status") }} AS closure_status,
  {{ nullif_placeholder("o.closure_deliverable_type") }} AS closure_deliverable_type,
  {{ nullif_placeholder("o.closure_deliverable") }} AS closure_deliverable,
  {{ nullif_placeholder("o.risk_content") }} AS risk_content,
  {{ nullif_placeholder("o.remark") }} AS remark,
  {{ nullif_placeholder("o.filled_by") }} AS filled_by,

  {{ parse_date_safe("o.plan_date") }} AS plan_date,
  {{ parse_date_safe("o.follow_up_date") }} AS follow_up_date,
  {{ parse_date_safe("o.final_closure_date") }} AS final_closure_date,
  {{ parse_date_safe("o.last_update_time") }} AS last_update_time,
  {{ parse_numeric_safe("o.plan_week") }}::int AS plan_week,
  {{ parse_numeric_safe("o.follow_up_week") }}::int AS follow_up_week,
  {{ parse_numeric_safe("o.final_closure_week") }}::int AS final_closure_week,
  {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week
FROM {{ source('pm_ods_v2', 'progress_measure_v2') }} o

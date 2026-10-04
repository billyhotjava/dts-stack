{{ config(materialized='view', tags=['project-management-v3', 'stg', 'tech-state']) }}

SELECT
  o.id AS source_row_id,
  'ods_tech_state_v2'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'excel') AS source_system,
  o._dts_import_time AS imported_at,

  {{ nullif_placeholder("o.project_no") }} AS project_no,
  {{ nullif_placeholder("o.tech_state_name") }} AS tech_state_name,
  {{ nullif_placeholder("o.change_item") }} AS change_item,
  {{ nullif_placeholder("o.owner") }} AS owner,
  {{ nullif_placeholder("o.dept") }} AS dept,
  {{ nullif_placeholder("o.dept_leader") }} AS dept_leader,
  {{ nullif_placeholder("o.change_reason") }} AS change_reason,
  {{ nullif_placeholder("o.affected_files") }} AS affected_files,
  {{ nullif_placeholder("o.affected_objects") }} AS affected_objects,
  {{ nullif_placeholder("o.project_manager") }} AS project_manager,
  {{ nullif_placeholder("o.filled_by") }} AS filled_by,
  {{ nullif_placeholder("o.remark") }} AS remark,

  {{ nullif_placeholder("o.completion_signature") }} AS completion_signature_raw,
  {{ nullif_placeholder("o.change_category") }} AS change_category_raw,
  {{ nullif_placeholder("o.plan_synced") }} AS plan_synced_raw,
  {{ nullif_placeholder("o.review_situation") }} AS review_situation_raw,
  {{ nullif_placeholder("o.file_signature_status") }} AS file_signature_status_raw,
  {{ nullif_placeholder("o.reform_status") }} AS reform_status_raw,

  {{ parse_numeric_safe("o.new_plan_count") }}::int AS new_plan_count,
  {{ parse_date_safe("o.change_submit_time") }} AS change_submit_time,
  {{ parse_date_safe("o.signature_closure_date") }} AS signature_closure_date,
  {{ parse_date_safe("o.plan_file_closure_date") }} AS plan_file_closure_date,
  {{ parse_date_safe("o.plan_reform_date") }} AS plan_reform_date,
  {{ parse_date_safe("o.file_signature_date") }} AS file_signature_date,
  {{ parse_date_safe("o.reform_date") }} AS reform_date,
  {{ parse_date_safe("o.last_update_time") }} AS last_update_time,
  {{ parse_numeric_safe("o.change_submit_week") }}::int AS change_submit_week,
  {{ parse_numeric_safe("o.signature_closure_week") }}::int AS signature_closure_week,
  {{ parse_numeric_safe("o.plan_file_closure_week") }}::int AS plan_file_closure_week,
  {{ parse_numeric_safe("o.plan_reform_week") }}::int AS plan_reform_week,
  {{ parse_numeric_safe("o.file_signature_week") }}::int AS file_signature_week,
  {{ parse_numeric_safe("o.reform_week") }}::int AS reform_week,
  {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week
FROM {{ source('pm_ods_v2', 'tech_state_v2') }} o

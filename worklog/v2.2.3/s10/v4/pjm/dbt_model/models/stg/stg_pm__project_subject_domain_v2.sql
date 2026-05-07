{{ config(materialized='view', tags=['project-management-v3', 'stg', 'progress']) }}

SELECT
  o.id AS source_row_id,
  'ods_project_subject_domain_v2'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'excel') AS source_system,
  o._dts_import_time AS imported_at,

  {{ nullif_placeholder("o.project_no") }} AS project_no,
  {{ nullif_placeholder("o.subsystem") }} AS subsystem,
  {{ nullif_placeholder("o.node_task") }} AS node_task,
  {{ nullif_placeholder("o.owner") }} AS owner,
  {{ nullif_placeholder("o.dept") }} AS dept,
  {{ nullif_placeholder("o.dept_leader") }} AS dept_leader,
  {{ nullif_placeholder("o.collab_dept") }} AS collab_dept,
  {{ nullif_placeholder("o.supervisor_dept") }} AS supervisor_dept,
  {{ nullif_placeholder("o.incomplete_reason") }} AS incomplete_reason,
  {{ nullif_placeholder("o.risk_content") }} AS risk_content,
  {{ nullif_placeholder("o.delay_impact") }} AS delay_impact,
  {{ nullif_placeholder("o.institute_leader") }} AS institute_leader,
  {{ nullif_placeholder("o.project_manager") }} AS project_manager,
  {{ nullif_placeholder("o.filled_by") }} AS filled_by,
  {{ nullif_placeholder("o.highlight") }} AS highlight,
  {{ nullif_placeholder("o.deliverable") }} AS deliverable,

  {{ nullif_placeholder("o.completion_status") }} AS completion_status_raw,
  {{ nullif_placeholder("o.node_type") }} AS node_type_raw,
  {{ nullif_placeholder("o.risk_level") }} AS risk_level_raw,
  {{ nullif_placeholder("o.delay_applied") }} AS delay_applied_raw,
  {{ nullif_placeholder("o.source") }} AS data_source,

  {{ nullif_placeholder("o.plan_date") }} AS plan_date_raw,
  {{ parse_date_safe("o.plan_start_date") }} AS plan_start_date,
  {{ parse_date_safe("o.plan_date") }} AS plan_date,
  {{ parse_date_safe("o.actual_start_date") }} AS actual_start_date,
  {{ parse_date_safe("o.actual_date") }} AS actual_date,
  {{ parse_date_safe("o.delay_expected_date") }} AS delay_expected_date,
  {{ parse_date_safe("o.original_plan_date") }} AS original_plan_date,
  {{ parse_date_safe("o.last_update_time") }} AS last_update_time,

  {{ parse_numeric_safe("o.plan_week") }}::int AS plan_week,
  {{ parse_numeric_safe("o.actual_week") }}::int AS actual_week,
  {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week
FROM {{ source('pm_ods_v2', 'project_subject_domain_v2') }} o

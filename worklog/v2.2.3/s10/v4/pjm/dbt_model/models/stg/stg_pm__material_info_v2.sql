{{ config(materialized='view', tags=['project-management-v3', 'stg', 'material']) }}

SELECT
  o.id::bigint AS source_row_id,
  COALESCE({{ nullif_placeholder("o._dts_source_table") }}, 'ods_material_info_v2') AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'excel') AS source_system,
  o._dts_import_time AS imported_at,

  {{ nullif_placeholder("o.project_no") }} AS project_no,
  {{ nullif_placeholder("o.subsystem") }} AS subsystem,
  {{ nullif_placeholder("o.pbs_no") }} AS pbs_no,
  {{ nullif_placeholder("o.pbs_name") }} AS pbs_name,
  {{ nullif_placeholder("o.self_or_outsource") }} AS self_or_outsource,
  {{ nullif_placeholder("o.supplier_name") }} AS supplier_name,
  {{ nullif_placeholder("o.is_long_cycle") }} AS is_long_cycle_raw,
  {{ nullif_placeholder("o.dept_owner") }} AS dept_owner,
  {{ nullif_placeholder("o.control_dept_owner") }} AS control_dept_owner,
  {{ nullif_placeholder("o.weekly_progress") }} AS weekly_progress,
  {{ nullif_placeholder("o.affects_major_node") }} AS affects_major_node_raw,
  {{ nullif_placeholder("o.risk_level") }} AS risk_level_raw,
  {{ nullif_placeholder("o.risk_content") }} AS risk_content,
  {{ nullif_placeholder("o.delay_impact") }} AS delay_impact,
  {{ nullif_placeholder("o.remark") }} AS remark,

  {{ parse_date_safe("o.contract_negotiation_date") }} AS contract_negotiation_date,
  {{ parse_date_safe("o.contract_delivery_date") }} AS contract_delivery_date,
  {{ parse_date_safe("o.actual_delivery_date") }} AS actual_delivery_date,
  {{ parse_date_safe("o.plan_inspect_date") }} AS plan_inspect_date,
  {{ parse_date_safe("o.complete_inspect_date") }} AS complete_inspect_date,
  {{ parse_date_safe("o.install_date") }} AS install_date,
  {{ parse_date_safe("o.last_update_time") }} AS last_update_time,
  {{ parse_numeric_safe("o.contract_negotiation_week") }}::int AS contract_negotiation_week,
  {{ parse_numeric_safe("o.contract_delivery_week") }}::int AS contract_delivery_week,
  {{ parse_numeric_safe("o.actual_delivery_week") }}::int AS actual_delivery_week,
  {{ parse_numeric_safe("o.plan_inspect_week") }}::int AS plan_inspect_week,
  {{ parse_numeric_safe("o.complete_inspect_week") }}::int AS complete_inspect_week,
  {{ parse_numeric_safe("o.install_week") }}::int AS install_week,
  {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week
FROM {{ source('pm_ods_v2', 'material_info_v2') }} o

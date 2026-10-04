{{ config(materialized='view', tags=['metro-app-pack', 'stg', 'training-contract']) }}

SELECT
  o.id AS source_row_id,
  'ods_metro_training_snapshot_schema'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'dts') AS source_system,
  o._dts_import_time AS imported_at,
  {{ nullif_placeholder("o._dts_batch_id") }} AS dts_batch_id,
  {{ nullif_placeholder("o._dts_execution_id") }} AS dts_execution_id,
  {{ nullif_placeholder("o._dts_task_id") }} AS dts_task_id,

  {{ nullif_placeholder("o.snapshot_id") }} AS snapshot_id,
  {{ nullif_placeholder("o.field_name") }} AS field_name,
  COALESCE({{ nullif_placeholder("o.field_role") }}, 'feature') AS field_role,
  {{ nullif_placeholder("o.data_type") }} AS data_type,
  COALESCE(o.is_required, false) AS is_required,
  COALESCE(o.nullable, true) AS nullable,
  {{ parse_numeric_safe("o.ordinal_position") }}::int AS ordinal_position,
  {{ parse_numeric_safe("o.feature_index") }}::int AS feature_index,
  {{ parse_numeric_safe("o.expected_min_value") }} AS expected_min_value,
  {{ parse_numeric_safe("o.expected_max_value") }} AS expected_max_value,
  o.enum_values::jsonb AS enum_values,
  {{ nullif_placeholder("o.description") }} AS description
FROM {{ source('metro_ods', 'training_snapshot_schema') }} o

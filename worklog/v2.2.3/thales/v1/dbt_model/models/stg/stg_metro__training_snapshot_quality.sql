{{ config(materialized='view', tags=['metro-app-pack', 'stg', 'training-contract']) }}

SELECT
  o.id AS source_row_id,
  'ods_metro_training_snapshot_quality'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'dts') AS source_system,
  o._dts_import_time AS imported_at,
  {{ nullif_placeholder("o._dts_batch_id") }} AS dts_batch_id,
  {{ nullif_placeholder("o._dts_execution_id") }} AS dts_execution_id,
  {{ nullif_placeholder("o._dts_task_id") }} AS dts_task_id,

  {{ nullif_placeholder("o.snapshot_id") }} AS snapshot_id,
  {{ nullif_placeholder("o.check_name") }} AS check_name,
  {{ nullif_placeholder("o.check_type") }} AS check_type,
  COALESCE(lower({{ nullif_placeholder("o.check_status") }}), 'unknown') AS check_status,
  COALESCE(lower({{ nullif_placeholder("o.severity") }}), 'warning') AS severity,
  {{ parse_numeric_safe("o.metric_value") }} AS metric_value,
  {{ parse_numeric_safe("o.threshold_value") }} AS threshold_value,
  {{ parse_numeric_safe("o.failed_count") }}::bigint AS failed_count,
  {{ parse_numeric_safe("o.sample_count") }}::bigint AS sample_count,
  {{ nullif_placeholder("o.message") }} AS message,
  NULLIF(o.checked_at::text, '')::timestamptz AS checked_at,
  o.quality_payload::jsonb AS quality_payload
FROM {{ source('metro_ods', 'training_snapshot_quality') }} o

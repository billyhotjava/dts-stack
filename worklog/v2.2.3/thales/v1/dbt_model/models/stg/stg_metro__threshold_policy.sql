{{ config(materialized='view', tags=['metro-app-pack', 'stg', 'expert-governance']) }}

SELECT
  o.id AS source_row_id,
  'ods_metro_signal_threshold_policy'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'metro-stack') AS source_system,
  o._dts_import_time AS imported_at,
  COALESCE({{ nullif_placeholder("o._dts_batch_id") }}, {{ nullif_placeholder("o.batch_id") }}) AS dts_batch_id,
  {{ nullif_placeholder("o._dts_execution_id") }} AS dts_execution_id,
  {{ nullif_placeholder("o._dts_task_id") }} AS dts_task_id,

  {{ nullif_placeholder("o.policy_id") }} AS policy_id,
  {{ nullif_placeholder("o.batch_id") }} AS batch_id,
  {{ nullif_placeholder("o.model_name") }} AS model_name,
  {{ nullif_placeholder("o.target_metric") }} AS target_metric,
  {{ nullif_placeholder("o.threshold_name") }} AS threshold_name,
  {{ parse_numeric_safe("o.threshold_value") }} AS threshold_value,
  {{ nullif_placeholder("o.threshold_unit") }} AS threshold_unit,
  {{ nullif_placeholder("o.policy_source") }} AS policy_source,
  o.policy_payload::jsonb AS policy_payload,
  {{ nullif_placeholder("o.updated_by") }} AS updated_by,
  NULLIF(o.updated_at::text, '')::timestamptz AS updated_at
FROM {{ source('metro_ods', 'threshold_policy') }} o

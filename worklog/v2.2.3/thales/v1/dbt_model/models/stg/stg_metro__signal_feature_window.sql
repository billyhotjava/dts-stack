{{ config(materialized='view', tags=['metro-app-pack', 'stg', 'lstm']) }}

SELECT
  o.id AS source_row_id,
  'ods_metro_signal_feature_window'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'metro-stack') AS source_system,
  o._dts_import_time AS imported_at,
  COALESCE({{ nullif_placeholder("o._dts_batch_id") }}, {{ nullif_placeholder("o.batch_id") }}) AS dts_batch_id,
  {{ nullif_placeholder("o._dts_execution_id") }} AS dts_execution_id,
  {{ nullif_placeholder("o._dts_task_id") }} AS dts_task_id,

  {{ nullif_placeholder("o.window_id") }} AS window_id,
  {{ nullif_placeholder("o.batch_id") }} AS batch_id,
  {{ nullif_placeholder("o.line_code") }} AS line_code,
  {{ nullif_placeholder("o.station_code") }} AS station_code,
  {{ nullif_placeholder("o.equipment_id") }} AS equipment_id,
  {{ nullif_placeholder("o.equipment_type") }} AS equipment_type,
  NULLIF(o.start_time::text, '')::timestamptz AS start_time,
  NULLIF(o.end_time::text, '')::timestamptz AS end_time,
  {{ parse_numeric_safe("o.sample_count") }}::int AS sample_count,
  o.feature_payload::jsonb AS feature_payload,
  o.quality_payload::jsonb AS quality_payload,
  o.expert_feature_payload::jsonb AS expert_feature_payload,
  {{ nullif_placeholder("o.weak_label") }} AS weak_label,
  COALESCE({{ parse_numeric_safe("o.sample_weight") }}, 1.0) AS sample_weight,
  COALESCE({{ nullif_placeholder("o.split") }}, 'train') AS split,
  {{ nullif_placeholder("o.source_file") }} AS source_file
FROM {{ source('metro_ods', 'signal_feature_window') }} o

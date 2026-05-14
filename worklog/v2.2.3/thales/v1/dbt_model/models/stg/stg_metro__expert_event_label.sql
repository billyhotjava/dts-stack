{{ config(materialized='view', tags=['metro-app-pack', 'stg', 'expert-governance']) }}

SELECT
  o.id AS source_row_id,
  'ods_metro_signal_expert_event_label'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'metro-stack') AS source_system,
  o._dts_import_time AS imported_at,
  COALESCE({{ nullif_placeholder("o._dts_batch_id") }}, {{ nullif_placeholder("o.batch_id") }}) AS dts_batch_id,
  {{ nullif_placeholder("o._dts_execution_id") }} AS dts_execution_id,
  {{ nullif_placeholder("o._dts_task_id") }} AS dts_task_id,

  {{ nullif_placeholder("o.event_id") }} AS event_id,
  {{ nullif_placeholder("o.batch_id") }} AS batch_id,
  {{ nullif_placeholder("o.window_id") }} AS window_id,
  {{ nullif_placeholder("o.equipment_id") }} AS equipment_id,
  {{ nullif_placeholder("o.label_type") }} AS label_type,
  {{ nullif_placeholder("o.expert_verdict") }} AS expert_verdict,
  {{ nullif_placeholder("o.severity") }} AS severity,
  COALESCE({{ parse_numeric_safe("o.confidence") }}, 0.0) AS confidence,
  {{ nullif_placeholder("o.reason_code") }} AS reason_code,
  {{ nullif_placeholder("o.expert_comment") }} AS expert_comment,
  {{ nullif_placeholder("o.expert_user") }} AS expert_user,
  NULLIF(o.reviewed_at::text, '')::timestamptz AS reviewed_at,
  {{ nullif_placeholder("o.source_file") }} AS source_file,
  CASE
    WHEN {{ nullif_placeholder("o.severity") }} IN ('critical', '严重', 'P0') THEN 4
    WHEN {{ nullif_placeholder("o.severity") }} IN ('high', '高', 'P1') THEN 3
    WHEN {{ nullif_placeholder("o.severity") }} IN ('medium', '中', 'P2') THEN 2
    WHEN {{ nullif_placeholder("o.severity") }} IN ('low', '低', 'P3') THEN 1
    ELSE 0
  END AS severity_score
FROM {{ source('metro_ods', 'expert_event_label') }} o

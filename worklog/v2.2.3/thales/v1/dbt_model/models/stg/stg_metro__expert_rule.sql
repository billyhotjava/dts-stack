{{ config(materialized='view', tags=['metro-app-pack', 'stg', 'expert-governance']) }}

SELECT
  o.id AS source_row_id,
  'ods_metro_signal_expert_rule'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'metro-stack') AS source_system,
  o._dts_import_time AS imported_at,
  {{ nullif_placeholder("o._dts_batch_id") }} AS dts_batch_id,
  {{ nullif_placeholder("o._dts_execution_id") }} AS dts_execution_id,
  {{ nullif_placeholder("o._dts_task_id") }} AS dts_task_id,

  {{ nullif_placeholder("o.rule_id") }} AS rule_id,
  {{ nullif_placeholder("o.rule_name") }} AS rule_name,
  {{ nullif_placeholder("o.rule_scope") }} AS rule_scope,
  {{ nullif_placeholder("o.equipment_type") }} AS equipment_type,
  {{ nullif_placeholder("o.feature_name") }} AS feature_name,
  {{ nullif_placeholder("o.operator") }} AS rule_operator,
  {{ parse_numeric_safe("o.threshold_value") }} AS threshold_value,
  {{ nullif_placeholder("o.severity") }} AS severity,
  COALESCE({{ parse_numeric_safe("o.confidence") }}, 0.0) AS confidence,
  COALESCE({{ nullif_placeholder("o.rule_status") }}, 'active') AS rule_status,
  NULLIF(o.effective_from::text, '')::timestamptz AS effective_from,
  NULLIF(o.effective_to::text, '')::timestamptz AS effective_to,
  {{ nullif_placeholder("o.expert_user") }} AS expert_user,
  o.rule_payload::jsonb AS rule_payload,
  CASE
    WHEN {{ nullif_placeholder("o.severity") }} IN ('critical', '严重', 'P0') THEN 4
    WHEN {{ nullif_placeholder("o.severity") }} IN ('high', '高', 'P1') THEN 3
    WHEN {{ nullif_placeholder("o.severity") }} IN ('medium', '中', 'P2') THEN 2
    WHEN {{ nullif_placeholder("o.severity") }} IN ('low', '低', 'P3') THEN 1
    ELSE 0
  END AS severity_score
FROM {{ source('metro_ods', 'expert_rule') }} o

{{ config(materialized='view', tags=['metro-app-pack', 'stg', 'training-contract']) }}

SELECT
  o.id AS source_row_id,
  'ods_metro_training_snapshot_lineage'::text AS source_table,
  COALESCE({{ nullif_placeholder("o._dts_source_system") }}, 'dts') AS source_system,
  o._dts_import_time AS imported_at,
  {{ nullif_placeholder("o._dts_batch_id") }} AS dts_batch_id,
  {{ nullif_placeholder("o._dts_execution_id") }} AS dts_execution_id,
  {{ nullif_placeholder("o._dts_task_id") }} AS dts_task_id,

  {{ nullif_placeholder("o.snapshot_id") }} AS snapshot_id,
  {{ nullif_placeholder("o.upstream_asset") }} AS upstream_asset,
  {{ nullif_placeholder("o.upstream_type") }} AS upstream_type,
  {{ nullif_placeholder("o.upstream_version") }} AS upstream_version,
  COALESCE({{ nullif_placeholder("o.relation_type") }}, 'derived_from') AS relation_type,
  {{ nullif_placeholder("o.owner") }} AS owner,
  o.lineage_payload::jsonb AS lineage_payload
FROM {{ source('metro_ods', 'training_snapshot_lineage') }} o

{{ config(materialized='table', tags=['metro-app-pack', 'dwd', 'training-contract']) }}

WITH manifest AS (
  SELECT *
  FROM {{ ref('stg_metro__training_snapshot_manifest') }}
),
schema_checks AS (
  SELECT
    s.snapshot_id,
    COUNT(*) AS field_count,
    COUNT(*) FILTER (WHERE s.field_role = 'feature') AS feature_field_count,
    COUNT(*) FILTER (WHERE s.is_required) AS required_field_count,
    BOOL_OR(s.field_name = 'window_id') AS has_window_id,
    BOOL_OR(s.field_name = 'window_start' OR s.field_name = 'start_time') AS has_window_start,
    BOOL_OR(s.field_name = 'window_end' OR s.field_name = 'end_time') AS has_window_end,
    BOOL_OR(s.field_name = 'split') AS has_split,
    BOOL_OR(s.field_role = 'feature') AS has_feature_columns
  FROM {{ ref('stg_metro__training_snapshot_schema') }} s
  GROUP BY s.snapshot_id
),
quality_checks AS (
  SELECT
    q.snapshot_id,
    COUNT(*) AS quality_check_count,
    COUNT(*) FILTER (
      WHERE q.check_status IN ('failed', 'error', 'blocked')
        AND q.severity IN ('blocker', 'critical', 'p0')
    ) AS blocker_failure_count,
    COUNT(*) FILTER (
      WHERE q.check_status IN ('warning', 'warn')
        OR q.severity IN ('warning', 'p1', 'p2')
    ) AS warning_count,
    MAX(q.checked_at) AS last_checked_at
  FROM {{ ref('stg_metro__training_snapshot_quality') }} q
  GROUP BY q.snapshot_id
),
lineage_checks AS (
  SELECT
    l.snapshot_id,
    COUNT(*) AS lineage_asset_count,
    COUNT(*) FILTER (WHERE l.upstream_type = 'dbt_model') AS dbt_lineage_count,
    COUNT(*) FILTER (WHERE l.upstream_type IN ('ods_table', 'source_table')) AS ods_lineage_count,
    COUNT(*) FILTER (WHERE l.upstream_type = 'expert_governance') AS expert_lineage_count
  FROM {{ ref('stg_metro__training_snapshot_lineage') }} l
  GROUP BY l.snapshot_id
)

SELECT
  m.snapshot_id,
  m.dataset_id,
  m.dataset_name,
  m.dbt_model_name,
  m.dbt_model_version,
  m.contract_version,
  m.data_format,
  m.data_uri,
  m.data_file_name,
  m.csv_delimiter,
  m.csv_encoding,
  m.csv_header,
  m.schema_uri,
  m.lineage_uri,
  m.quality_report_uri,
  m.entity_column,
  m.time_column,
  m.split_column,
  m.window_size,
  m.step_size,
  m.feature_count,
  m.row_count,
  m.window_count,
  m.generated_at,
  m.data_start_time,
  m.data_end_time,
  COALESCE(sc.field_count, 0) AS field_count,
  COALESCE(sc.feature_field_count, 0) AS feature_field_count,
  COALESCE(sc.required_field_count, 0) AS required_field_count,
  COALESCE(sc.has_window_id, false) AS has_window_id,
  COALESCE(sc.has_window_start, false) AS has_window_start,
  COALESCE(sc.has_window_end, false) AS has_window_end,
  COALESCE(sc.has_split, false) AS has_split,
  COALESCE(sc.has_feature_columns, false) AS has_feature_columns,
  COALESCE(qc.quality_check_count, 0) AS quality_check_count,
  COALESCE(qc.blocker_failure_count, 0) AS blocker_failure_count,
  COALESCE(qc.warning_count, 0) AS warning_count,
  qc.last_checked_at,
  COALESCE(lc.lineage_asset_count, 0) AS lineage_asset_count,
  COALESCE(lc.dbt_lineage_count, 0) AS dbt_lineage_count,
  COALESCE(lc.ods_lineage_count, 0) AS ods_lineage_count,
  COALESCE(lc.expert_lineage_count, 0) AS expert_lineage_count,
  CASE
    WHEN m.snapshot_id IS NULL THEN 'blocked'
    WHEN m.data_format <> 'csv' THEN 'blocked'
    WHEN m.data_uri IS NULL THEN 'blocked'
    WHEN lower(COALESCE(m.data_file_name, '')) <> 'data.csv' THEN 'blocked'
    WHEN COALESCE(m.csv_header, true) = false THEN 'blocked'
    WHEN m.window_size IS NULL OR m.window_size < 2 THEN 'blocked'
    WHEN m.feature_count IS NULL OR m.feature_count < 1 THEN 'blocked'
    WHEN COALESCE(sc.has_window_id, false) = false THEN 'blocked'
    WHEN COALESCE(sc.has_window_start, false) = false THEN 'blocked'
    WHEN COALESCE(sc.has_window_end, false) = false THEN 'blocked'
    WHEN COALESCE(sc.has_split, false) = false THEN 'blocked'
    WHEN COALESCE(sc.has_feature_columns, false) = false THEN 'blocked'
    WHEN COALESCE(qc.blocker_failure_count, 0) > 0 THEN 'blocked'
    WHEN COALESCE(sc.feature_field_count, 0) <> m.feature_count THEN 'warning'
    WHEN COALESCE(qc.warning_count, 0) > 0 THEN 'warning'
    WHEN COALESCE(lc.lineage_asset_count, 0) = 0 THEN 'warning'
    ELSE 'passed'
  END AS contract_status,
  CASE
    WHEN m.data_format <> 'csv' THEN 'training snapshot must be exported as csv'
    WHEN m.data_uri IS NULL THEN 'data_uri is required'
    WHEN lower(COALESCE(m.data_file_name, '')) <> 'data.csv' THEN 'data_file_name must be data.csv'
    WHEN COALESCE(m.csv_header, true) = false THEN 'csv header row is required'
    WHEN m.window_size IS NULL OR m.window_size < 2 THEN 'window_size is invalid'
    WHEN m.feature_count IS NULL OR m.feature_count < 1 THEN 'feature_count is invalid'
    WHEN COALESCE(sc.has_window_id, false) = false THEN 'window_id is required'
    WHEN COALESCE(sc.has_window_start, false) = false THEN 'window_start/start_time is required'
    WHEN COALESCE(sc.has_window_end, false) = false THEN 'window_end/end_time is required'
    WHEN COALESCE(sc.has_split, false) = false THEN 'split is required'
    WHEN COALESCE(sc.has_feature_columns, false) = false THEN 'feature columns are required'
    WHEN COALESCE(qc.blocker_failure_count, 0) > 0 THEN 'blocker quality checks failed'
    WHEN COALESCE(sc.feature_field_count, 0) <> m.feature_count THEN 'declared feature_count does not match schema feature fields'
    WHEN COALESCE(qc.warning_count, 0) > 0 THEN 'quality checks contain warnings'
    WHEN COALESCE(lc.lineage_asset_count, 0) = 0 THEN 'lineage metadata is missing'
    ELSE 'ready for metro-stack contract validation'
  END AS contract_message,
  now() AS refreshed_at
FROM manifest m
LEFT JOIN schema_checks sc
  ON sc.snapshot_id IS NOT DISTINCT FROM m.snapshot_id
LEFT JOIN quality_checks qc
  ON qc.snapshot_id IS NOT DISTINCT FROM m.snapshot_id
LEFT JOIN lineage_checks lc
  ON lc.snapshot_id IS NOT DISTINCT FROM m.snapshot_id

{{ config(materialized='table', tags=['metro-app-pack', 'dwd', 'lstm', 'training-snapshot']) }}

WITH training_windows AS (
  SELECT *
  FROM {{ ref('metro_dwd_lstm_training_window') }}
),
contracts AS (
  SELECT *
  FROM {{ ref('metro_dwd_lstm_training_contract') }}
)

SELECT
  COALESCE(w.training_snapshot_id, c.snapshot_id) AS training_snapshot_id,
  c.contract_status,
  c.contract_message,
  c.dataset_id,
  c.dataset_name,
  c.dbt_model_name,
  c.dbt_model_version,
  c.contract_version,
  c.data_format,
  c.data_uri,
  c.data_file_name,
  c.csv_delimiter,
  c.csv_encoding,
  c.csv_header,
  c.entity_column,
  c.time_column,
  c.split_column,
  c.window_size,
  c.step_size,
  c.feature_count,
  w.source_row_id,
  w.source_table,
  w.source_system,
  w.imported_at,
  w.dts_batch_id,
  w.dts_execution_id,
  w.dts_task_id,
  w.window_id,
  w.batch_id,
  w.line_code,
  w.station_code,
  w.equipment_id,
  w.equipment_type,
  w.start_time AS window_start,
  w.end_time AS window_end,
  w.sample_count,
  w.feature_payload,
  w.quality_payload,
  w.expert_feature_payload,
  w.weak_label,
  w.sample_weight,
  w.split,
  w.expert_label_count,
  w.expert_severity_score,
  w.expert_confidence,
  w.expert_marked_anomaly,
  w.expert_rule_count,
  w.expert_rule_severity_score,
  w.expert_rule_confidence,
  w.training_label,
  CONCAT(COALESCE(w.split, 'train'), '/', COALESCE(w.equipment_type, 'unknown')) AS snapshot_segment_key
FROM training_windows w
LEFT JOIN contracts c
  ON c.snapshot_id IS NOT DISTINCT FROM w.training_snapshot_id

{{ config(materialized='table', tags=['metro-app-pack', 'ads', 'demo']) }}

SELECT
  COALESCE(batch_id, dts_batch_id, 'unknown') AS batch_id,
  training_snapshot_id,
  MAX(contract_status) AS contract_status,
  MAX(data_format) AS data_format,
  MAX(parquet_uri) AS parquet_uri,
  COUNT(*) AS training_window_count,
  COUNT(*) FILTER (WHERE training_label <> 'unlabeled') AS labeled_window_count,
  COUNT(*) FILTER (WHERE expert_marked_anomaly) AS expert_anomaly_window_count,
  SUM(expert_label_count) AS expert_label_count,
  MAX(expert_severity_score) AS max_expert_severity_score,
  AVG(expert_confidence) AS avg_expert_confidence,
  MAX(expert_rule_count) AS max_expert_rule_count,
  MIN(start_time) AS min_window_start_time,
  MAX(end_time) AS max_window_end_time,
  now() AS refreshed_at
FROM {{ ref('metro_dwd_lstm_training_snapshot') }}
GROUP BY
  COALESCE(batch_id, dts_batch_id, 'unknown'),
  training_snapshot_id

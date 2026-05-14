{{ config(materialized='table', tags=['metro-app-pack', 'dwd', 'lstm']) }}

WITH windows AS (
  SELECT *
  FROM {{ ref('stg_metro__signal_feature_window') }}
),
labels AS (
  SELECT
    batch_id,
    window_id,
    COUNT(*) AS expert_label_count,
    MAX(severity_score) AS expert_severity_score,
    AVG(confidence) AS expert_confidence,
    BOOL_OR(expert_verdict IN ('anomaly', '异常', 'fault', '故障')) AS expert_marked_anomaly
  FROM {{ ref('stg_metro__expert_event_label') }}
  GROUP BY batch_id, window_id
),
rules AS (
  SELECT
    equipment_type,
    COUNT(*) FILTER (WHERE rule_status = 'active') AS active_rule_count,
    MAX(severity_score) FILTER (WHERE rule_status = 'active') AS max_rule_severity_score,
    AVG(confidence) FILTER (WHERE rule_status = 'active') AS avg_rule_confidence
  FROM {{ ref('stg_metro__expert_rule') }}
  GROUP BY equipment_type
)

SELECT
  w.source_row_id,
  w.source_table,
  w.source_system,
  w.imported_at,
  w.dts_batch_id,
  w.dts_execution_id,
  w.dts_task_id,
  COALESCE(w.dts_batch_id, w.batch_id) AS training_snapshot_id,
  w.window_id,
  w.batch_id,
  w.line_code,
  w.station_code,
  w.equipment_id,
  w.equipment_type,
  w.start_time,
  w.end_time,
  w.sample_count,
  w.feature_payload,
  w.quality_payload,
  w.expert_feature_payload,
  w.weak_label,
  w.sample_weight,
  w.split,
  COALESCE(l.expert_label_count, 0) AS expert_label_count,
  COALESCE(l.expert_severity_score, 0) AS expert_severity_score,
  COALESCE(l.expert_confidence, 0.0) AS expert_confidence,
  COALESCE(l.expert_marked_anomaly, false) AS expert_marked_anomaly,
  COALESCE(r.active_rule_count, 0) AS expert_rule_count,
  COALESCE(r.max_rule_severity_score, 0) AS expert_rule_severity_score,
  COALESCE(r.avg_rule_confidence, 0.0) AS expert_rule_confidence,
  CASE
    WHEN COALESCE(l.expert_marked_anomaly, false) THEN 'expert_anomaly'
    WHEN w.weak_label IS NOT NULL THEN w.weak_label
    ELSE 'unlabeled'
  END AS training_label
FROM windows w
LEFT JOIN labels l
  ON l.batch_id IS NOT DISTINCT FROM w.batch_id
 AND l.window_id IS NOT DISTINCT FROM w.window_id
LEFT JOIN rules r
  ON r.equipment_type IS NOT DISTINCT FROM w.equipment_type

COPY (
  SELECT
    window_id,
    batch_id,
    line_code,
    station_code,
    equipment_id,
    equipment_type,
    to_char(start_time AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM-DD"T"HH24:MI:SS') AS start_time,
    to_char(end_time AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM-DD"T"HH24:MI:SS') AS end_time,
    sample_count,
    feature_payload::text AS feature_payload,
    quality_payload::text AS quality_payload,
    expert_feature_payload::text AS expert_feature_payload,
    weak_label,
    sample_weight,
    split,
    expert_label_count,
    expert_severity_score,
    expert_confidence,
    expert_rule_count,
    expert_rule_severity_score,
    expert_rule_confidence,
    training_label
  FROM public.metro_dwd_lstm_training_window
  WHERE batch_id = 'demo-metro-20260513'
  ORDER BY start_time
) TO '/tmp/metro_dwd_lstm_training_window.csv' WITH CSV HEADER;

-- Metro App-Pack demo seed data for biadmin.public ODS tables.
-- Batch id is fixed so the script is idempotent for repeated demos.

BEGIN;

DELETE FROM public.ods_metro_signal_telemetry_point
WHERE batch_id = 'demo-metro-20260513';

DELETE FROM public.ods_metro_signal_feature_window
WHERE batch_id = 'demo-metro-20260513';

DELETE FROM public.ods_metro_signal_expert_event_label
WHERE batch_id = 'demo-metro-20260513';

DELETE FROM public.ods_metro_signal_threshold_policy
WHERE batch_id = 'demo-metro-20260513';

DELETE FROM public.ods_metro_signal_expert_rule
WHERE _dts_batch_id = 'demo-metro-20260513';

DELETE FROM public.ods_metro_training_snapshot_manifest
WHERE snapshot_id = 'demo-metro-20260513';

DELETE FROM public.ods_metro_training_snapshot_schema
WHERE snapshot_id = 'demo-metro-20260513';

DELETE FROM public.ods_metro_training_snapshot_quality
WHERE snapshot_id = 'demo-metro-20260513';

DELETE FROM public.ods_metro_training_snapshot_lineage
WHERE snapshot_id = 'demo-metro-20260513';

WITH sample(
  n,
  start_ts,
  equipment_id,
  current_a,
  voltage_v,
  temperature_c,
  switch_gap_mm,
  vibration_g,
  response_ms,
  weak_label,
  sample_weight,
  split,
  rule_hits,
  expert_risk,
  missing_ratio,
  quality_score
) AS (
  VALUES
    (1,  '2026-05-13T09:00:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.20, 220.0, 31.0, 2.10, 0.030,  92, 'normal',         1.00, 'train', 0, 0.10, 0.01, 0.98),
    (2,  '2026-05-13T09:01:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.24, 221.0, 31.4, 2.09, 0.031,  94, 'normal',         1.00, 'train', 0, 0.10, 0.01, 0.98),
    (3,  '2026-05-13T09:02:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.28, 220.5, 32.0, 2.08, 0.032,  96, 'normal',         1.00, 'train', 0, 0.12, 0.01, 0.98),
    (4,  '2026-05-13T09:03:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.35, 222.0, 32.6, 2.06, 0.035, 101, 'normal',         1.00, 'train', 0, 0.16, 0.02, 0.96),
    (5,  '2026-05-13T09:04:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.40, 221.5, 33.1, 2.04, 0.037, 108, 'normal',         1.00, 'train', 0, 0.18, 0.02, 0.96),
    (6,  '2026-05-13T09:05:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.42, 222.5, 33.4, 2.03, 0.038, 112, 'normal',         1.00, 'train', 0, 0.20, 0.02, 0.95),
    (7,  '2026-05-13T09:06:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.55, 224.0, 35.8, 1.94, 0.052, 138, 'watch',          1.10, 'train', 1, 0.45, 0.03, 0.93),
    (8,  '2026-05-13T09:07:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.63, 225.0, 37.2, 1.89, 0.058, 151, 'watch',          1.15, 'train', 1, 0.52, 0.03, 0.92),
    (9,  '2026-05-13T09:08:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 2.85, 231.0, 48.5, 1.55, 0.142, 238, 'expert_anomaly', 1.50, 'valid', 2, 0.88, 0.04, 0.89),
    (10, '2026-05-13T09:09:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 2.70, 230.5, 47.8, 1.58, 0.136, 226, 'expert_anomaly', 1.45, 'valid', 2, 0.84, 0.04, 0.90),
    (11, '2026-05-13T09:10:00+08:00'::timestamptz, 'SIG-L1-S02-SW03', 1.72, 226.0, 39.0, 1.83, 0.071, 164, 'watch',          1.20, 'valid', 1, 0.58, 0.03, 0.91),
    (12, '2026-05-13T09:11:00+08:00'::timestamptz, 'SIG-L1-S02-SW03', 1.36, 222.0, 33.0, 2.04, 0.039, 110, 'normal',         1.00, 'valid', 0, 0.18, 0.02, 0.96)
)
INSERT INTO public.ods_metro_signal_feature_window (
  window_id,
  batch_id,
  line_code,
  station_code,
  equipment_id,
  equipment_type,
  start_time,
  end_time,
  sample_count,
  feature_payload,
  quality_payload,
  expert_feature_payload,
  weak_label,
  sample_weight,
  split,
  source_file,
  _dts_source_system,
  _dts_source_table,
  _dts_batch_id,
  _dts_execution_id,
  _dts_task_id
)
SELECT
  'demo-metro-win-' || lpad(n::text, 3, '0') AS window_id,
  'demo-metro-20260513' AS batch_id,
  'L1' AS line_code,
  CASE WHEN equipment_id LIKE '%S02%' THEN 'S02' ELSE 'S01' END AS station_code,
  equipment_id,
  'switch' AS equipment_type,
  start_ts,
  start_ts + interval '60 seconds',
  120 AS sample_count,
  jsonb_build_object(
    'current_a', current_a,
    'voltage_v', voltage_v,
    'temperature_c', temperature_c,
    'switch_gap_mm', switch_gap_mm,
    'vibration_g', vibration_g,
    'response_ms', response_ms
  ) AS feature_payload,
  jsonb_build_object(
    'missing_ratio', missing_ratio,
    'quality_score', quality_score
  ) AS quality_payload,
  jsonb_build_object(
    'rule_hits', rule_hits,
    'expert_risk', expert_risk
  ) AS expert_feature_payload,
  weak_label,
  sample_weight,
  split,
  'dts-demo-seed' AS source_file,
  'metro-stack' AS _dts_source_system,
  'ods_seed_demo_data.sql' AS _dts_source_table,
  'demo-metro-20260513' AS _dts_batch_id,
  'demo-exec-20260513' AS _dts_execution_id,
  'demo-seed' AS _dts_task_id
FROM sample;

WITH sample(
  n,
  start_ts,
  equipment_id,
  current_a,
  voltage_v,
  temperature_c,
  switch_gap_mm,
  vibration_g,
  response_ms
) AS (
  VALUES
    (1,  '2026-05-13T09:00:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.20, 220.0, 31.0, 2.10, 0.030,  92),
    (2,  '2026-05-13T09:01:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.24, 221.0, 31.4, 2.09, 0.031,  94),
    (3,  '2026-05-13T09:02:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.28, 220.5, 32.0, 2.08, 0.032,  96),
    (4,  '2026-05-13T09:03:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.35, 222.0, 32.6, 2.06, 0.035, 101),
    (5,  '2026-05-13T09:04:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.40, 221.5, 33.1, 2.04, 0.037, 108),
    (6,  '2026-05-13T09:05:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.42, 222.5, 33.4, 2.03, 0.038, 112),
    (7,  '2026-05-13T09:06:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.55, 224.0, 35.8, 1.94, 0.052, 138),
    (8,  '2026-05-13T09:07:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 1.63, 225.0, 37.2, 1.89, 0.058, 151),
    (9,  '2026-05-13T09:08:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 2.85, 231.0, 48.5, 1.55, 0.142, 238),
    (10, '2026-05-13T09:09:00+08:00'::timestamptz, 'SIG-L1-S01-SW01', 2.70, 230.5, 47.8, 1.58, 0.136, 226),
    (11, '2026-05-13T09:10:00+08:00'::timestamptz, 'SIG-L1-S02-SW03', 1.72, 226.0, 39.0, 1.83, 0.071, 164),
    (12, '2026-05-13T09:11:00+08:00'::timestamptz, 'SIG-L1-S02-SW03', 1.36, 222.0, 33.0, 2.04, 0.039, 110)
),
metrics AS (
  SELECT
    n,
    start_ts,
    equipment_id,
    metric_code,
    metric_value,
    metric_unit
  FROM sample
  CROSS JOIN LATERAL (
    VALUES
      ('current_a', current_a, 'A'),
      ('temperature_c', temperature_c, 'C'),
      ('response_ms', response_ms, 'ms')
  ) AS m(metric_code, metric_value, metric_unit)
)
INSERT INTO public.ods_metro_signal_telemetry_point (
  batch_id,
  line_code,
  station_code,
  equipment_id,
  equipment_type,
  signal_code,
  signal_name,
  sample_time,
  metric_code,
  metric_value,
  metric_unit,
  quality_status,
  raw_payload,
  _dts_source_system,
  _dts_source_table,
  _dts_batch_id,
  _dts_execution_id,
  _dts_task_id
)
SELECT
  'demo-metro-20260513',
  'L1',
  CASE WHEN equipment_id LIKE '%S02%' THEN 'S02' ELSE 'S01' END,
  equipment_id,
  'switch',
  equipment_id || ':' || metric_code,
  metric_code,
  start_ts,
  metric_code,
  metric_value,
  metric_unit,
  'valid',
  jsonb_build_object('window_id', 'demo-metro-win-' || lpad(n::text, 3, '0')),
  'metro-stack',
  'ods_seed_demo_data.sql',
  'demo-metro-20260513',
  'demo-exec-20260513',
  'demo-seed'
FROM metrics;

INSERT INTO public.ods_metro_signal_expert_event_label (
  event_id,
  batch_id,
  window_id,
  equipment_id,
  label_type,
  expert_verdict,
  severity,
  confidence,
  reason_code,
  expert_comment,
  expert_user,
  reviewed_at,
  source_file,
  _dts_source_system,
  _dts_source_table,
  _dts_batch_id,
  _dts_execution_id,
  _dts_task_id
)
VALUES
  ('demo-evt-001', 'demo-metro-20260513', 'demo-metro-win-009', 'SIG-L1-S01-SW01', 'alert_event', 'anomaly',        'high',   0.92, 'SWITCH_GAP_TEMP', '转辙机温升和动作响应同时异常，建议保留为正样本。', 'expert.zhang', '2026-05-13T09:20:00+08:00', 'demo-expert-log.csv', 'metro-stack', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-evt-002', 'demo-metro-20260513', 'demo-metro-win-011', 'SIG-L1-S02-SW03', 'alert_event', 'false_positive', 'low',    0.78, 'MAINTENANCE',      '该窗口处于检修切换，作为误报样本参与阈值校准。',             'expert.li',    '2026-05-13T09:25:00+08:00', 'demo-expert-log.csv', 'metro-stack', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed');

INSERT INTO public.ods_metro_signal_expert_rule (
  rule_id,
  rule_name,
  rule_scope,
  equipment_type,
  feature_name,
  operator,
  threshold_value,
  severity,
  confidence,
  rule_status,
  effective_from,
  effective_to,
  expert_user,
  rule_payload,
  _dts_source_system,
  _dts_source_table,
  _dts_batch_id,
  _dts_execution_id,
  _dts_task_id
)
VALUES
  ('metro-rule-switch-temp', '转辙机温升专家规则', 'equipment_type', 'switch', 'temperature_c', '>=', 45.0, 'high',   0.90, 'active', '2026-05-13T00:00:00+08:00', NULL, 'expert.zhang', '{"explain":"温度超过 45C 且响应延迟增加时，提高异常权重"}'::jsonb, 'metro-stack', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('metro-rule-switch-gap',  '转辙机缺口偏移专家规则', 'equipment_type', 'switch', 'switch_gap_mm', '<=', 1.60, 'medium', 0.86, 'active', '2026-05-13T00:00:00+08:00', NULL, 'expert.li',    '{"explain":"缺口低于 1.60mm 时进入观察区间"}'::jsonb,                'metro-stack', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed');

INSERT INTO public.ods_metro_signal_threshold_policy (
  policy_id,
  batch_id,
  model_name,
  target_metric,
  threshold_name,
  threshold_value,
  threshold_unit,
  policy_source,
  policy_payload,
  updated_by,
  updated_at,
  _dts_source_system,
  _dts_source_table,
  _dts_batch_id,
  _dts_execution_id,
  _dts_task_id
)
VALUES
  ('demo-metro-static-quantile', 'demo-metro-20260513', 'lstm-ae', 'reconstruction_score', 'STATIC_QUANTILE', 0.995, '', 'expert_override', '{"reason":"演示阶段降低误报，先用高分位静态阈值"}'::jsonb, 'expert.zhang', '2026-05-13T09:30:00+08:00', 'metro-stack', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-min-windows',     'demo-metro-20260513', 'lstm-ae', 'alert_event',          'MIN_EVENT_WINDOWS', 2.000, 'window', 'expert_override', '{"reason":"至少连续 2 个窗口异常才生成事件"}'::jsonb,      'expert.li',    '2026-05-13T09:30:00+08:00', 'metro-stack', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed');

INSERT INTO public.ods_metro_training_snapshot_manifest (
  snapshot_id,
  dataset_id,
  dataset_name,
  dbt_model_name,
  dbt_model_version,
  data_format,
  data_uri,
  data_file_name,
  csv_delimiter,
  csv_encoding,
  csv_header,
  schema_uri,
  lineage_uri,
  quality_report_uri,
  entity_column,
  time_column,
  split_column,
  window_size,
  step_size,
  feature_count,
  row_count,
  window_count,
  generated_at,
  data_start_time,
  data_end_time,
  contract_version,
  status,
  manifest_payload,
  _dts_source_system,
  _dts_source_table,
  _dts_batch_id,
  _dts_execution_id,
  _dts_task_id
)
VALUES (
  'demo-metro-20260513',
  'dts.dataset.metro.lstm.training.window',
  '地铁 LSTM 训练窗口快照',
  'metro_dwd_lstm_training_snapshot',
  'v1',
  'csv',
  'dts://datasets/metro/lstm/demo-metro-20260513/data.csv',
  'data.csv',
  ',',
  'utf-8',
  true,
  'dts://datasets/metro/lstm/demo-metro-20260513/schema.json',
  'dts://datasets/metro/lstm/demo-metro-20260513/lineage.json',
  'dts://datasets/metro/lstm/demo-metro-20260513/quality_report.json',
  'equipment_id',
  'start_time',
  'split',
  120,
  20,
  8,
  12,
  12,
  '2026-05-13T09:35:00+08:00',
  '2026-05-13T09:00:00+08:00',
  '2026-05-13T09:12:00+08:00',
  'metro-lstm-contract-v1',
  'ready',
  '{"exporter":"dts-csv","package_files":["manifest.json","schema.json","quality_report.json","lineage.json","data.csv"],"delimiter":",","encoding":"utf-8","header":true}'::jsonb,
  'dts',
  'ods_seed_demo_data.sql',
  'demo-metro-20260513',
  'demo-exec-20260513',
  'demo-seed'
);

INSERT INTO public.ods_metro_training_snapshot_schema (
  snapshot_id,
  field_name,
  field_role,
  data_type,
  is_required,
  nullable,
  ordinal_position,
  feature_index,
  enum_values,
  description,
  _dts_source_system,
  _dts_source_table,
  _dts_batch_id,
  _dts_execution_id,
  _dts_task_id
)
VALUES
  ('demo-metro-20260513', 'window_id',       'key',     'string',    true,  false,  1, NULL, NULL, '窗口唯一标识', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'equipment_id',    'entity',  'string',    true,  false,  2, NULL, NULL, '设备唯一标识', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'start_time',      'time',    'timestamp', true,  false,  3, NULL, NULL, '窗口开始时间', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'end_time',        'time',    'timestamp', true,  false,  4, NULL, NULL, '窗口结束时间', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'split',           'split',   'string',    true,  false,  5, NULL, '["train","valid","test"]'::jsonb, '训练/验证/测试分区', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'current_a',       'feature', 'double',    true,  false,  6,    0, NULL, '动作电流', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'voltage_v',       'feature', 'double',    true,  false,  7,    1, NULL, '动作电压', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'temperature_c',   'feature', 'double',    true,  false,  8,    2, NULL, '设备温度', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'switch_gap_mm',   'feature', 'double',    true,  false,  9,    3, NULL, '转辙机缺口', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'vibration_g',     'feature', 'double',    true,  false, 10,    4, NULL, '振动强度', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'response_ms',     'feature', 'double',    true,  false, 11,    5, NULL, '动作响应时延', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'rule_hits',       'feature', 'double',    true,  false, 12,    6, NULL, '专家规则命中数', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'expert_risk',     'feature', 'double',    true,  false, 13,    7, NULL, '专家风险评分', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'training_label',  'label',   'string',    false, true,  14, NULL, '["normal","watch","expert_anomaly","unlabeled"]'::jsonb, '弱标签或专家标签', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'sample_weight',   'weight',  'double',    false, true,  15, NULL, NULL, '样本权重', 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed');

INSERT INTO public.ods_metro_training_snapshot_quality (
  snapshot_id,
  check_name,
  check_type,
  check_status,
  severity,
  metric_value,
  threshold_value,
  failed_count,
  sample_count,
  message,
  checked_at,
  quality_payload,
  _dts_source_system,
  _dts_source_table,
  _dts_batch_id,
  _dts_execution_id,
  _dts_task_id
)
VALUES
  ('demo-metro-20260513', 'required_fields_present', 'schema', 'passed', 'info', 1.0, 1.0, 0, 15, '必填字段已满足训练契约', '2026-05-13T09:35:00+08:00', '{}'::jsonb, 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'feature_count_match', 'schema', 'passed', 'info', 8.0, 8.0, 0, 8, 'manifest feature_count 与 schema feature 字段数一致', '2026-05-13T09:35:00+08:00', '{}'::jsonb, 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'time_order_continuity', 'timeseries', 'passed', 'info', 1.0, 1.0, 0, 12, '设备内时间顺序可排序，演示样本无断点', '2026-05-13T09:35:00+08:00', '{"window_size":120,"step_size":20}'::jsonb, 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'expert_governance_coverage', 'governance', 'passed', 'info', 0.25, 0.10, 0, 12, '专家标注和规则可追溯', '2026-05-13T09:35:00+08:00', '{}'::jsonb, 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed');

INSERT INTO public.ods_metro_training_snapshot_lineage (
  snapshot_id,
  upstream_asset,
  upstream_type,
  upstream_version,
  relation_type,
  owner,
  lineage_payload,
  _dts_source_system,
  _dts_source_table,
  _dts_batch_id,
  _dts_execution_id,
  _dts_task_id
)
VALUES
  ('demo-metro-20260513', 'public.ods_metro_signal_feature_window', 'ods_table', 'v1', 'derived_from', 'DTS 数据治理', '{}'::jsonb, 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'public.ods_metro_signal_expert_event_label', 'expert_governance', 'v1', 'joined_with', '地铁信号专家', '{}'::jsonb, 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'public.metro_dwd_lstm_training_window', 'dbt_model', 'v1', 'materialized_as', 'DTS dbt', '{}'::jsonb, 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed'),
  ('demo-metro-20260513', 'dts://datasets/metro/lstm/demo-metro-20260513/data.csv', 'csv_snapshot', 'v1', 'exported_to', 'DTS 数据集服务', '{"format":"csv","delimiter":",","encoding":"utf-8","header":true}'::jsonb, 'dts', 'ods_seed_demo_data.sql', 'demo-metro-20260513', 'demo-exec-20260513', 'demo-seed');

COMMIT;

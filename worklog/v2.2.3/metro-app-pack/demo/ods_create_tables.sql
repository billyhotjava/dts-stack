-- Metro App-Pack demo ODS tables.
-- ODS keeps source fidelity and DTS trace fields; STG/DWD models converge training semantics.

CREATE TABLE IF NOT EXISTS public.ods_metro_signal_telemetry_point (
  id BIGSERIAL PRIMARY KEY,
  batch_id TEXT,
  line_code TEXT,
  station_code TEXT,
  equipment_id TEXT,
  equipment_type TEXT,
  signal_code TEXT,
  signal_name TEXT,
  sample_time TIMESTAMPTZ,
  metric_code TEXT,
  metric_value NUMERIC,
  metric_unit TEXT,
  quality_status TEXT,
  raw_payload JSONB,
  _dts_source_system TEXT DEFAULT 'metro-stack',
  _dts_source_table TEXT,
  _dts_import_time TIMESTAMPTZ DEFAULT now(),
  _dts_batch_id TEXT,
  _dts_execution_id TEXT,
  _dts_task_id TEXT
);

CREATE TABLE IF NOT EXISTS public.ods_metro_signal_feature_window (
  id BIGSERIAL PRIMARY KEY,
  window_id TEXT NOT NULL,
  batch_id TEXT,
  line_code TEXT,
  station_code TEXT,
  equipment_id TEXT,
  equipment_type TEXT,
  start_time TIMESTAMPTZ,
  end_time TIMESTAMPTZ,
  sample_count INTEGER,
  feature_payload JSONB,
  quality_payload JSONB,
  expert_feature_payload JSONB,
  weak_label TEXT,
  sample_weight NUMERIC,
  split TEXT,
  source_file TEXT,
  _dts_source_system TEXT DEFAULT 'metro-stack',
  _dts_source_table TEXT,
  _dts_import_time TIMESTAMPTZ DEFAULT now(),
  _dts_batch_id TEXT,
  _dts_execution_id TEXT,
  _dts_task_id TEXT
);

CREATE TABLE IF NOT EXISTS public.ods_metro_signal_expert_event_label (
  id BIGSERIAL PRIMARY KEY,
  event_id TEXT NOT NULL,
  batch_id TEXT,
  window_id TEXT,
  equipment_id TEXT,
  label_type TEXT,
  expert_verdict TEXT,
  severity TEXT,
  confidence NUMERIC,
  reason_code TEXT,
  expert_comment TEXT,
  expert_user TEXT,
  reviewed_at TIMESTAMPTZ,
  source_file TEXT,
  _dts_source_system TEXT DEFAULT 'metro-stack',
  _dts_source_table TEXT,
  _dts_import_time TIMESTAMPTZ DEFAULT now(),
  _dts_batch_id TEXT,
  _dts_execution_id TEXT,
  _dts_task_id TEXT
);

CREATE TABLE IF NOT EXISTS public.ods_metro_signal_expert_rule (
  id BIGSERIAL PRIMARY KEY,
  rule_id TEXT NOT NULL,
  rule_name TEXT,
  rule_scope TEXT,
  equipment_type TEXT,
  feature_name TEXT,
  operator TEXT,
  threshold_value NUMERIC,
  severity TEXT,
  confidence NUMERIC,
  rule_status TEXT,
  effective_from TIMESTAMPTZ,
  effective_to TIMESTAMPTZ,
  expert_user TEXT,
  rule_payload JSONB,
  _dts_source_system TEXT DEFAULT 'metro-stack',
  _dts_source_table TEXT,
  _dts_import_time TIMESTAMPTZ DEFAULT now(),
  _dts_batch_id TEXT,
  _dts_execution_id TEXT,
  _dts_task_id TEXT
);

CREATE TABLE IF NOT EXISTS public.ods_metro_signal_threshold_policy (
  id BIGSERIAL PRIMARY KEY,
  policy_id TEXT NOT NULL,
  batch_id TEXT,
  model_name TEXT,
  target_metric TEXT,
  threshold_name TEXT,
  threshold_value NUMERIC,
  threshold_unit TEXT,
  policy_source TEXT,
  policy_payload JSONB,
  updated_by TEXT,
  updated_at TIMESTAMPTZ,
  _dts_source_system TEXT DEFAULT 'metro-stack',
  _dts_source_table TEXT,
  _dts_import_time TIMESTAMPTZ DEFAULT now(),
  _dts_batch_id TEXT,
  _dts_execution_id TEXT,
  _dts_task_id TEXT
);

CREATE INDEX IF NOT EXISTS idx_ods_metro_feature_window_batch
  ON public.ods_metro_signal_feature_window (batch_id, window_id);

CREATE INDEX IF NOT EXISTS idx_ods_metro_feature_window_equipment_time
  ON public.ods_metro_signal_feature_window (equipment_id, start_time);

CREATE INDEX IF NOT EXISTS idx_ods_metro_expert_label_batch_window
  ON public.ods_metro_signal_expert_event_label (batch_id, window_id);

CREATE INDEX IF NOT EXISTS idx_ods_metro_expert_rule_scope
  ON public.ods_metro_signal_expert_rule (rule_status, equipment_type, feature_name);

CREATE INDEX IF NOT EXISTS idx_ods_metro_threshold_policy_batch
  ON public.ods_metro_signal_threshold_policy (batch_id, model_name, target_metric);

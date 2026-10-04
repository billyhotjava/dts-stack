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

CREATE TABLE IF NOT EXISTS public.ods_metro_training_snapshot_manifest (
  id BIGSERIAL PRIMARY KEY,
  snapshot_id TEXT NOT NULL,
  dataset_id TEXT,
  dataset_name TEXT,
  dbt_model_name TEXT,
  dbt_model_version TEXT,
  data_format TEXT DEFAULT 'csv',
  data_uri TEXT,
  data_file_name TEXT DEFAULT 'data.csv',
  csv_delimiter TEXT DEFAULT ',',
  csv_encoding TEXT DEFAULT 'utf-8',
  csv_header BOOLEAN DEFAULT true,
  schema_uri TEXT,
  lineage_uri TEXT,
  quality_report_uri TEXT,
  entity_column TEXT,
  time_column TEXT,
  split_column TEXT,
  window_size INTEGER,
  step_size INTEGER,
  feature_count INTEGER,
  row_count BIGINT,
  window_count BIGINT,
  generated_at TIMESTAMPTZ,
  data_start_time TIMESTAMPTZ,
  data_end_time TIMESTAMPTZ,
  contract_version TEXT DEFAULT 'metro-lstm-contract-v1',
  status TEXT,
  manifest_payload JSONB,
  _dts_source_system TEXT DEFAULT 'dts',
  _dts_source_table TEXT,
  _dts_import_time TIMESTAMPTZ DEFAULT now(),
  _dts_batch_id TEXT,
  _dts_execution_id TEXT,
  _dts_task_id TEXT
);

ALTER TABLE public.ods_metro_training_snapshot_manifest
  ALTER COLUMN data_format SET DEFAULT 'csv';

ALTER TABLE public.ods_metro_training_snapshot_manifest
  ADD COLUMN IF NOT EXISTS data_uri TEXT,
  ADD COLUMN IF NOT EXISTS data_file_name TEXT DEFAULT 'data.csv',
  ADD COLUMN IF NOT EXISTS csv_delimiter TEXT DEFAULT ',',
  ADD COLUMN IF NOT EXISTS csv_encoding TEXT DEFAULT 'utf-8',
  ADD COLUMN IF NOT EXISTS csv_header BOOLEAN DEFAULT true;

CREATE TABLE IF NOT EXISTS public.ods_metro_training_snapshot_schema (
  id BIGSERIAL PRIMARY KEY,
  snapshot_id TEXT NOT NULL,
  field_name TEXT NOT NULL,
  field_role TEXT,
  data_type TEXT,
  is_required BOOLEAN DEFAULT false,
  nullable BOOLEAN DEFAULT true,
  ordinal_position INTEGER,
  feature_index INTEGER,
  expected_min_value NUMERIC,
  expected_max_value NUMERIC,
  enum_values JSONB,
  description TEXT,
  _dts_source_system TEXT DEFAULT 'dts',
  _dts_source_table TEXT,
  _dts_import_time TIMESTAMPTZ DEFAULT now(),
  _dts_batch_id TEXT,
  _dts_execution_id TEXT,
  _dts_task_id TEXT
);

CREATE TABLE IF NOT EXISTS public.ods_metro_training_snapshot_quality (
  id BIGSERIAL PRIMARY KEY,
  snapshot_id TEXT NOT NULL,
  check_name TEXT NOT NULL,
  check_type TEXT,
  check_status TEXT,
  severity TEXT,
  metric_value NUMERIC,
  threshold_value NUMERIC,
  failed_count BIGINT,
  sample_count BIGINT,
  message TEXT,
  checked_at TIMESTAMPTZ,
  quality_payload JSONB,
  _dts_source_system TEXT DEFAULT 'dts',
  _dts_source_table TEXT,
  _dts_import_time TIMESTAMPTZ DEFAULT now(),
  _dts_batch_id TEXT,
  _dts_execution_id TEXT,
  _dts_task_id TEXT
);

CREATE TABLE IF NOT EXISTS public.ods_metro_training_snapshot_lineage (
  id BIGSERIAL PRIMARY KEY,
  snapshot_id TEXT NOT NULL,
  upstream_asset TEXT NOT NULL,
  upstream_type TEXT,
  upstream_version TEXT,
  relation_type TEXT,
  owner TEXT,
  lineage_payload JSONB,
  _dts_source_system TEXT DEFAULT 'dts',
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

CREATE INDEX IF NOT EXISTS idx_ods_metro_snapshot_manifest_id
  ON public.ods_metro_training_snapshot_manifest (snapshot_id);

CREATE INDEX IF NOT EXISTS idx_ods_metro_snapshot_schema_id_role
  ON public.ods_metro_training_snapshot_schema (snapshot_id, field_role, field_name);

CREATE INDEX IF NOT EXISTS idx_ods_metro_snapshot_quality_id_status
  ON public.ods_metro_training_snapshot_quality (snapshot_id, check_status, severity);

CREATE INDEX IF NOT EXISTS idx_ods_metro_snapshot_lineage_id_type
  ON public.ods_metro_training_snapshot_lineage (snapshot_id, upstream_type);

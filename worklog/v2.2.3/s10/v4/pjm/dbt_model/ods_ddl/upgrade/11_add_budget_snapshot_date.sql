-- 预算 ODS 业务时间语义升级：仅新增可空 snapshot_date。
-- 历史数据回填与结构迁移分离，由受控数据脚本单独执行。
\set ON_ERROR_STOP on

ALTER TABLE ods_budget_v2
    ADD COLUMN IF NOT EXISTS snapshot_date date;

COMMENT ON COLUMN ods_budget_v2.snapshot_date IS
    '业务快照日期；不得使用 _dts_import_time 代替';

SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name = 'ods_budget_v2'
  AND column_name = 'snapshot_date';

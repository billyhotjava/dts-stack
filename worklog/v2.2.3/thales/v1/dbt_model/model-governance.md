# Thales Metro Model Governance

## 分层约定

- ODS 保留现场采集、DTS 批次追溯、专家标注和模型策略原始字段。
- STG 只做类型收敛、占位符清洗、JSON 字段保留和严重度评分。
- DWD 承接训练语义，把专家经验转成 LSTM 可消费的维度：
  - `training_snapshot_id`
  - `expert_label_count`
  - `expert_severity_score`
  - `expert_confidence`
  - `expert_marked_anomaly`
  - `expert_rule_count`
  - `expert_rule_severity_score`
  - `expert_rule_confidence`
  - `training_label`
- 训练快照契约由 `metro_dwd_lstm_training_contract` 承载，统一校验 manifest、schema、quality、lineage。
- `metro_dwd_lstm_training_snapshot` 是给 metro-stack 导出的训练快照模型，正式口径由 DTS 导出为 CSV 训练快照包。
- ADS 仅用于演示汇总，不承载训练明细。

## 命名约定

- ODS: `ods_metro_signal_*`
- STG: `stg_metro__*`
- DWD: `metro_dwd_*`
- ADS: `metro_ads_*`

## 训练快照契约

DTS 向 metro-stack 交付训练数据时，需要同时交付四类契约资产：

- `ods_metro_training_snapshot_manifest`：快照 ID、CSV 数据 URI、dbt 模型版本、窗口长度、特征数。
- `ods_metro_training_snapshot_schema`：字段角色、类型、必填约束和特征序号。
- `ods_metro_training_snapshot_quality`：字段完整性、类型一致性、时序连续性、专家覆盖率等检查。
- `ods_metro_training_snapshot_lineage`：ODS、dbt 模型、专家治理表和 CSV 快照导出作业血缘。

`metro_dwd_lstm_training_contract.contract_status` 作为训练前门禁：

- `passed`：允许训练。
- `warning`：允许训练，但上线门禁继续暴露风险。
- `blocked`：阻断训练任务。

## 上线前检查

- 先执行 `ods_ddl/ods_create_tables.sql`。
- 确认 `metro_sources.yml` 中 ODS 表均存在。
- 优先构建 `metro_dwd_lstm_training_contract` 和 `metro_dwd_lstm_training_snapshot`，再构建 ADS 汇总。
- 专家治理表由 metro-stack 回流时，按 `ods_metro_signal_expert_event_label`、`ods_metro_signal_expert_rule`、`ods_metro_signal_threshold_policy` 三类 ODS 表接入。

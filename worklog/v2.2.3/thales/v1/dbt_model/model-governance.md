# Thales Metro Model Governance

## 分层约定

- ODS 保留现场采集、DTS 批次追溯、专家标注和模型策略原始字段。
- STG 只做类型收敛、占位符清洗、JSON 字段保留和严重度评分。
- DWD 承接训练语义，把专家经验转成 LSTM 可消费的维度：
  - `expert_label_count`
  - `expert_severity_score`
  - `expert_confidence`
  - `expert_marked_anomaly`
  - `expert_rule_count`
  - `expert_rule_severity_score`
  - `expert_rule_confidence`
  - `training_label`
- ADS 仅用于演示汇总，不承载训练明细。

## 命名约定

- ODS: `ods_metro_signal_*`
- STG: `stg_metro__*`
- DWD: `metro_dwd_*`
- ADS: `metro_ads_*`

## 上线前检查

- 先执行 `ods_ddl/ods_create_tables.sql`。
- 确认 `metro_sources.yml` 中 ODS 表均存在。
- 优先构建 `metro_dwd_lstm_training_window`，再构建 ADS 汇总。
- 专家治理表由 metro-stack 回流时，按 `ods_metro_signal_expert_event_label`、`ods_metro_signal_expert_rule`、`ods_metro_signal_threshold_policy` 三类 ODS 表接入。

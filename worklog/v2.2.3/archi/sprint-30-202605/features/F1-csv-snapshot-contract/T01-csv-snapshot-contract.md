# T01: 固化 CSV snapshot contract

**优先级**: P0  
**状态**: DONE  
**依赖**: 无

## 目标

定义正式训练快照包契约，替代演示阶段的 Parquet-ready 口径。

## 技术设计

契约固定为五件套目录：

```text
manifest.json
schema.json
quality_report.json
lineage.json
data.csv
```

`manifest.json` 必须包含：

```json
{
  "snapshot_id": "metro-lstm-20260514-001",
  "contract_version": "metro-lstm-contract-v1",
  "data_format": "csv",
  "data_uri": "data.csv",
  "entity_column": "equipment_id",
  "time_column": "window_start",
  "split_column": "split",
  "window_size": 120,
  "stride": 10,
  "feature_count": 42
}
```

`schema.json` 中字段角色限定为：`key`、`entity`、`time`、`split`、`feature`、`label`、`governance`。

## 影响范围

- `worklog/v2.2.3/thales/v1/dbt_model/model-governance.md`
- `worklog/v2.2.3/thales/v1/dbt_model/models/metro_schema.yml`
- metro-stack 的 `contract_validation.py`

## 验证

- [x] 手工检查契约样例中无 `parquet_uri` 强依赖。
- [x] metro-stack 契约校验测试覆盖 `data_format=csv`。

## 完成标准

- [x] 文档明确 CSV 是治理后训练快照，不是原始 CSV。
- [x] 字段角色和质量检查规则足以驱动 metro-stack 窗口构造。

## 完成记录

- 2026-05-14：`worklog/v2.2.3/thales/v1/dbt_model` 已切换为 CSV snapshot contract。
- 2026-05-14：metro-stack 契约测试覆盖完整 CSV 快照、缺 `data.csv`、无表头和 schema/header 不一致场景。

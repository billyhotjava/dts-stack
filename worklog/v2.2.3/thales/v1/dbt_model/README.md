# Thales Metro dbt Model v1

面向地铁信号系统设备的 App-Pack demo dbt 包，用于演示 DTS 数据治理平台到 metro-stack LSTM 模型训练链路。

## 目录结构

```text
dbt_model/
├── dbt_project.yml
├── models.tsv
├── model-governance.md
├── macros/
│   ├── nullif_placeholder.sql
│   └── parse_numeric_safe.sql
├── models/
│   ├── metro_sources.yml
│   ├── metro_schema.yml
│   ├── stg/
│   ├── dwd/
│   └── ads/
└── ods_ddl/
    ├── ods_create_tables.sql
    └── ods_seed_demo_data.sql
```

## ODS 初始化

首次演示前在目标 PostgreSQL 库执行：

```bash
psql -f ods_ddl/ods_create_tables.sql
```

需要演示数据时再执行：

```bash
psql -f ods_ddl/ods_seed_demo_data.sql
```

## 模型链路

```text
ods_metro_signal_feature_window
ods_metro_signal_expert_event_label
ods_metro_signal_expert_rule
ods_metro_signal_threshold_policy
        ↓
stg_metro__*
        ↓
metro_dwd_lstm_training_window
metro_dwd_expert_governance_signal
        ↓
metro_ads_app_pack_demo_summary
```

## UI 导入

1. 在 DTS 数据开发中心导入 `thales-metro-dbt-model.zip`。
2. 检查 `models.tsv` 导入的 7 个模型记录。
3. 构建 dbt 模型，选择 `tag:thales-metro` 或具体模型名。
4. 构建成功后上线模型。

## 给 metro-stack 的导出

构建成功后，`metro_dwd_lstm_training_window` 可导出为 CSV，供 metro-stack 的 DTS 批次入口读取。

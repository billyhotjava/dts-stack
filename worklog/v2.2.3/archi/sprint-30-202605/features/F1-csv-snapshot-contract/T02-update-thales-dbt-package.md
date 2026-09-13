# T02: 更新 thales dbt zip 与 ODS 初始化 SQL

**优先级**: P0  
**状态**: DONE  
**依赖**: T01

## 目标

把 `worklog/v2.2.3/thales/v1/` 下的地铁 dbt 包更新为 CSV snapshot contract 版本，并重新打包 zip。

## 技术设计

需要检查和更新：

- `dbt_model/ods_ddl/ods_create_tables.sql`
- `dbt_model/models/metro_sources.yml`
- `dbt_model/models/metro_schema.yml`
- `dbt_model/models/stg/*`
- `dbt_model/models/dwd/*`
- `dbt_model/models.tsv`
- `thales-metro-dbt-model.zip`

dbt 模型应保留：

- `metro_dwd_lstm_training_contract`
- `metro_dwd_lstm_training_snapshot`
- `stg_metro__training_snapshot_manifest`
- `stg_metro__training_snapshot_schema`
- `stg_metro__training_snapshot_quality`
- `stg_metro__training_snapshot_lineage`

## 影响范围

- DTS UI 导入 dbt zip 的模型列表。
- 后续 DTS 快照导出服务读取的模型和字段。

## 验证

- [x] `dbt parse --profiles-dir <临时 profile>` 通过。
- [x] `unzip -t thales-metro-dbt-model.zip` 通过。
- [x] zip 内包含 `dbt_model/models.tsv` 和 `dbt_model/ods_ddl/ods_create_tables.sql`。

## 完成标准

- [x] 用户可从 DTS UI 导入新的 dbt zip。
- [x] ODS 初始化 SQL 与模型 source 定义一致。

## 完成记录

- 2026-05-14：重建 `worklog/v2.2.3/thales/v1/thales-metro-dbt-model.zip`。
- 2026-05-14：执行 `dbt parse --project-dir worklog/v2.2.3/thales/v1/dbt_model --profiles-dir services/dts-dbt/profiles` 通过。
- 2026-05-14：执行 `unzip -t worklog/v2.2.3/thales/v1/thales-metro-dbt-model.zip` 通过。

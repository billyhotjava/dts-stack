# T04: CatalogAssetIdentityResolver 扩展

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T03

## 目标

把 `CatalogAssetIdentityResolver` 从以 `DATASET` 为主的解析器扩展为统一资产身份解析器，使代码化资产、metric-pack 资产和 scoped dataset 都能回到同一个 `asset_type + asset_key + asset_id` 契约。

## 待完成范围

- [x] 支持 `GLOSSARY_TERM`、`DATA_STANDARD`、`GOV_INDICATOR`、`MODELING_SQL_MODEL`、`METRIC_PACK` 当前版本解析。
- [x] 支持 `CatalogAssetKey.codeAsset(...)` 与 `metricPack(...)` 当前版本反向解析。
- [x] Resolver 返回值包含可用于 `asset_grant` 的 stable asset id / key。
- [ ] 对历史 UUID、OpenMetadata FQN、dbt model name、metric-pack ref 提供兼容代理。
- [ ] `API_SERVICE`、`scopedDataset(...)` 无 repository 命中的反向解析和解析失败审计报告继续补齐。

## 验收建议

- [x] 增加 `GLOSSARY_TERM` / `DATA_STANDARD` / `GOV_INDICATOR` / `MODELING_SQL_MODEL` / `METRIC_PACK` identity 单测。
- [ ] 增加同名不同 tenant/env/dialect 的不冲突测试。
- [ ] 增加 resolver 失败审计或 failure report 证据。

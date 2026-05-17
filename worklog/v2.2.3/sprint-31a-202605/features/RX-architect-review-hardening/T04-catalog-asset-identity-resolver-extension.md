# T04: CatalogAssetIdentityResolver 扩展

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

把 `CatalogAssetIdentityResolver` 从以 `DATASET` 为主的解析器扩展为统一资产身份解析器，使代码化资产、metric-pack 资产和 scoped dataset 都能回到同一个 `asset_type + asset_key + asset_id` 契约。

## 待完成范围

- [ ] 支持 `GLOSSARY_TERM`、`DATA_STANDARD`、`GOV_INDICATOR`、`MODELING_SQL_MODEL`、`METRIC_PACK`、`API_SERVICE`。
- [ ] 支持 `CatalogAssetKey.codeAsset(...)`、`metricPack(...)`、`scopedDataset(...)` 的反向解析。
- [ ] Resolver 返回值必须包含可用于 `asset_grant` 的 stable asset id / key。
- [ ] 对历史 UUID、OpenMetadata FQN、dbt model name、metric-pack ref 提供兼容代理。
- [ ] 解析失败时输出可审计原因，不允许静默 fallback 到默认 dataset。

## 验收建议

- 增加 `GLOSSARY_TERM` / `GOV_INDICATOR` / `METRIC_PACK` identity 单测。
- 增加同名不同 tenant/env/dialect 的不冲突测试。
- 增加 resolver 失败审计或 failure report 证据。

# T04: CatalogAssetIdentityResolver 扩展

**优先级**: P0
**状态**: DONE
**依赖**: T03

## 目标

把 `CatalogAssetIdentityResolver` 从以 `DATASET` 为主的解析器扩展为统一资产身份解析器，使代码化资产、metric-pack 资产和 scoped dataset 都能回到同一个 `asset_type + asset_key + asset_id` 契约。

## 待完成范围

- [x] 支持 `GLOSSARY_TERM`、`DATA_STANDARD`、`GOV_INDICATOR`、`MODELING_SQL_MODEL`、`METRIC_PACK` 当前版本解析。
- [x] 支持 `API_SERVICE` 当前版本解析。
- [x] 支持 `CatalogAssetKey.codeAsset(...)` 与 `metricPack(...)` 当前版本反向解析。
- [x] 支持 `CatalogAssetKey.scopedDataset(...)` 反向解析，即使本地 repository 未命中也返回稳定 `DATASET` identity。
- [x] Resolver 返回值包含可用于 `asset_grant` 的 stable asset id / key。
- [x] 对历史 UUID、OpenMetadata FQN、dbt model name、metric-pack ref 提供当前版本兼容代理。
- [x] `API_SERVICE`、`scopedDataset(...)` 无 repository 命中的反向解析已补齐。

## 验收建议

- [x] 增加 `GLOSSARY_TERM` / `DATA_STANDARD` / `GOV_INDICATOR` / `MODELING_SQL_MODEL` / `METRIC_PACK` identity 单测。
- [x] 增加 `API_SERVICE` identity 单测。
- [x] 增加 `scopedDataset(...)` key 反向解析单测。
- [ ] resolver 失败审计或 failure report 证据继续作为后续观测增强，不阻塞当前版本 identity contract。

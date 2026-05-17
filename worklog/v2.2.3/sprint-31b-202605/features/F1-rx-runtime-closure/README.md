# F1: RX 残余运行时收口

**优先级**: P0
**状态**: READY

## 目标

收尾 Sprint-31A RX/T03 与 RX/T04 未闭环的运行时项：替换 `findAll().stream().filter(...)` 性能热点，补齐 `API_SERVICE` / `scopedDataset(...)` 反向解析与历史兼容代理，落地 resolver 失败审计，并把剩余高频 code asset 写入 `CodeAssetGrantWriter`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | ModelingSqlModelRepository 索引方法替换全表扫描 | P0 | READY | - |
| T02 | CatalogDatasetRepository policy hot-path 索引方法 | P0 | READY | - |
| T03 | IdentityResolver 接入 API_SERVICE / scopedDataset 反向解析 | P0 | READY | T01 |
| T04 | IdentityResolver 历史兼容代理（UUID / OM FQN / dbt model name / metric-pack ref） | P0 | READY | T01, T03 |
| T05 | Resolver 失败审计与 failure report | P1 | READY | T03, T04 |
| T06 | 剩余 code asset writer（DataStandard / Glossary / SvcApi） | P0 | READY | Sprint-31A RX/T03 |

## 完成标准

- [ ] resolver / policy hot path 不存在全表扫描。
- [ ] resolver 失败必产生审计记录，不再 silent fallback。
- [ ] DataStandard / Glossary / SvcApi 三类高频 code asset 写入 `asset_ownership` + 自动 `MANAGE` grant。
- [ ] 同名不同 tenant/env/dialect 的 scoped 资产不会互相冲突，单测覆盖。

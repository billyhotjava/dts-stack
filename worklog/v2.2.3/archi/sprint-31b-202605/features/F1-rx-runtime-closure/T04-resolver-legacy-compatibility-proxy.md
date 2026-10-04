# T04: IdentityResolver 历史兼容代理

**优先级**: P0
**状态**: DONE
**依赖**: T01, T03

## 目标

让 `CatalogAssetIdentityResolver` 对历史 UUID、OpenMetadata FQN、dbt model name、metric-pack ref 都能解析到当前事实源契约，避免下游因为旧 ref 格式静默 fallback。

## 背景

Sprint-31A RX/T04 已支持新 ref 格式，但历史数据中存在：

| 旧 ref 形态 | 来源 | 当前行为 |
|---|---|---|
| `urn:uuid:xxxx` | OpenMetadata 旧导出 | resolver 无法匹配，返回 empty |
| `default.contracts.dwd_flower_contract_detail` | OpenMetadata FQN | 命中 DATASET 但 code asset 类型丢失 |
| `dwd_flower_contract_detail` | dbt model name（无 typeHint） | 命中 MODELING_SQL_MODEL（全表扫，T01 后已修），但与同名 DATASET 不区分优先级 |
| `flowerbiz/metric-pack:flower-rental:0.1.0` | metric-pack ref | 已处理 |

下游 metric-pack / analytics 旧数据迁移会撞这些 ref，必须兜底。

## 技术设计

1. 增加 `LegacyAssetRefAdapter`（service 内部 helper）：
   - 接收原始 ref，按优先级尝试：
     1. `urn:uuid:` 前缀 → 提取 UUID 走 `resolveCodeAssetById`/dataset
     2. `default.<db>.<table>` 形态 → 走 OpenMetadata FQN
     3. 纯 model name 无 typeHint → 优先 MODELING_SQL_MODEL，再 fallback DATASET，明确顺序
     4. `metric-pack:` / `glossary.` / `data_standard.` 前缀走现有解析
2. `resolveIdentity(String ref)` 增加 adapter 第一层，若 adapter 命中直接返回；否则走现有逻辑。
3. 解析优先级冲突时，在结果里附带 `legacyHint` 字段（如 `"resolved-by": "legacy-om-fqn"`），方便审计与排查。
4. 不允许在 adapter 内 silent fallback，匹配失败必须返回 `Optional.empty()` 由 T05 审计。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetIdentityResolver.java`
- 新增 `LegacyAssetRefAdapter`（内部 static class 或独立 helper）

## 验证

- [ ] `CatalogAssetIdentityResolverTest.resolvesLegacyUrnUuid`
- [ ] `CatalogAssetIdentityResolverTest.resolvesLegacyOpenMetadataFqn`
- [ ] `CatalogAssetIdentityResolverTest.modelNameAmbiguousPrefersSqlModelOverDataset`
- [ ] grep 搜索 `assets-v2/migration/dry-run` 输出确认历史 ref 覆盖率

## 当前状态（2026-05-17）

- 已覆盖 UUID、`urn:uuid:`、OpenMetadata entity id/FQN、纯 dbt/model 名称、metric-pack ref 的主路径。
- 冲突优先级审计和 failure report 证据转入 F1/T05。

## 完成标准

- [x] 4 类历史 ref 形态均有解析路径与单测。
- [x] 解析优先级有文档记录；失败审计转入 F1/T05。

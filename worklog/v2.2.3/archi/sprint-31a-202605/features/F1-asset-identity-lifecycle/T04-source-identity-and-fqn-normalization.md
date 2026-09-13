# T04: 来源标识和外部 FQN 归一

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

统一 OpenMetadata FQN、dbt unique_id、数据库 schema/table、Addax tableMapping 和 OpenLineage dataset name 的映射关系。

## 技术设计

- 保留 `sourceSystem`、`sourceId`、`externalFqn`、`dbtUniqueId`、`lineageDatasetName` 等来源证明。
- 避免前端或 dts-metrics 直接猜测 FQN。
- 同一资产多来源命中时记录匹配来源和 fallback reason。

## 影响范围

- OpenMetadataService
- DbtAssetSyncService
- OpenLineageReceiverResource
- CatalogAssetPortalService

## 验证

- [x] OpenMetadata 命中时能输出 stable source reference。
- [x] legacy Catalog 命中时能输出 source/schema/table 稳定 key。
- [ ] dbt/OpenLineage 来源证明在 F3 继续接入。

## 完成标准

- [x] 资产身份契约能说明来源和匹配依据；资产详情展示在 F5 中接入。

# Sprint-31A F1/T04 来源标识和外部 FQN 归一

**状态**: DONE
**日期**: 2026-05-17

## 目标

把来源标识从“调用方自己理解 UUID/FQN/sourceName”收敛为资产身份上的稳定来源证明，供资产详情、迁移 dry-run、血缘写入和 dts-metrics 引用。

## 来源证明字段

| 字段 | 含义 |
|---|---|
| sourceSystem | 来源系统，如 `dts-catalog`、`openmetadata`、`dbt`、`openlineage` |
| externalId | 来源系统内的主 ID，如 OM entity id、legacy dataset id、dbt unique id |
| fqn | 来源系统提供的完整名称或平台生成 key |
| sourceId | DTS 数据源 ID 或外部 service name |
| resolvedBy | 当前解析命中策略 |

## 已落地代码

- `CatalogAssetSourceReference`: 来源证明 record。
- `CatalogAssetIdentityResolver.resolveIdentity`: 输出 `sourceRef.stableRef()`，不再只返回 `resolvedBy`。
- `CatalogAssetKeyTest`: 增加 OpenMetadata source reference 规则测试，按执行约束暂不运行。

## 规则

- OpenMetadata 优先使用 `omEntityId` 作为 external id，FQN 保留为解释字段。
- legacy Catalog 优先使用 `CatalogDataset.id`，同时保留基于 source/schema/table 的稳定 key。
- 资产详情和 dry-run 报告应展示 source reference，而不是让调用方自行猜测命中来源。

## 后续

- dbt unique_id、OpenLineage dataset name、Addax tableMapping 的来源证明在 F3 中继续接入。
- F1/T05 将基于 source reference 输出历史资产冲突报告。

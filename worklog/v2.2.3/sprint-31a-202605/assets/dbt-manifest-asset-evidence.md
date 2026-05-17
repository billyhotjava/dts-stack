# Sprint-31A F3/T02 dbt manifest 资产同步证据

## 目标

dbt manifest 同步不只创建表和血缘，还要给资产事实源提供可追溯的构建证据，让 DWD/DWS/ADS 成为 `dts-metrics` 的可信输入。

## 当前落地

增强 `DbtAssetSyncService.syncFromManifest`：

- 新建或同步 CatalogDataset 时补 `snapshotTime`。
- 缺生命周期时写入 `PENDING_GOVERNANCE`。
- 缺描述时写入 manifest 证据：`uniqueId/schema/table/layer/assetKey/originalFilePath`。
- dbt 表级血缘 notes 写入 manifest 证据：`uniqueId/project/upstreamDatasetId/downstreamDatasetId`。
- 同步结果和审计 payload 增加 `manifestEvidenceUpdated`。

## 发布门禁可用信息

| 信息 | 来源 |
|------|------|
| 资产层级 | `warehouseLayer` |
| 治理状态 | Sprint-31A F2 契约 |
| 字段 contract | `catalog_column_schema` + F2/T03 API |
| 表级血缘 | `catalog_dataset_lineage` |
| 字段级血缘 | `catalog_column_lineage` |
| 构建证据 | `description` / lineage notes / sync stats |

## 后续依赖

- Sprint-31 发布门禁读取 `manifestEvidenceUpdated` 和治理缺口 API，判断是否允许 DWS/ADS/BI Dataset 发布。
- Sprint-32 `dts-metrics` 通过 F2/T02/T03 API 读取 DWD/DWS/ADS 候选资产，不直接解析 manifest。

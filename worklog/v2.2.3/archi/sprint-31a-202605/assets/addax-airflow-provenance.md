# Sprint-31A F3/T03 Addax/Airflow 来源证明

## 目标

把接入层任务、数据源、源表、ODS 表和运行实例写入资产来源证明，让 Sprint-31 黄金链路可以从 ODS 资产追溯到数据源与 Addax/Airflow 运行。

## 当前落地

增强 `IngestionLineageWriter`：

- 自动创建 source / ODS 资产时写入 `PENDING_GOVERNANCE` 生命周期。
- ODS 资产缺生命周期时补 `PENDING_GOVERNANCE`。
- Addax 血缘 notes 写入 `sourceAssetKey` 与 `targetAssetKey`。
- 继续保留 executionId、executionStatus、batchId、mappingId、sourceDataSourceId、sourceTable、targetTable。

## 资产证明

| 证明项 | 来源 |
|--------|------|
| 数据源 ID | `InfraOdsTableMapping.connectionId` |
| 源表 | `streamNamespace.streamName` |
| ODS 表 | `odsSchema.odsTable` |
| 任务名 | mapping description 中的任务名 |
| 运行实例 | `LineageObservation.executionId/batchId` |
| 资产键 | `CatalogAssetKey.dataset(...)` |

## 后续依赖

- F3/T05 血缘失败报告使用该证明定位缺失映射。
- Sprint-31 Connector Center / 发布门禁使用该证明串起接入层到资产目录的链路。

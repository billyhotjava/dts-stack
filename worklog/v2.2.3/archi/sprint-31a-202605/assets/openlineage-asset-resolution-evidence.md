# Sprint-31A F3/T01 OpenLineage 资产解析增强

## 目标

OpenLineage 接收端成为资产事实链的一部分：解析已有资产时返回统一资产证据，无法解析时创建 `PENDING_GOVERNANCE` 资产，并记录来源证明。

## 当前落地

增强端点：

```text
POST /api/internal/lineage/openlineage
```

## 解析证据

响应新增：

| 字段 | 说明 |
|------|------|
| `createdAssets` | 本次 OpenLineage 事件自动创建的资产数 |
| `assetEvidence` | 输入/输出资产解析证据 |

`assetEvidence` 包含：

- `role`: `UPSTREAM` 或 `DOWNSTREAM`
- `datasetId`: platform CatalogDataset ID
- `namespace`: OpenLineage namespace
- `rawName`: OpenLineage 原始 name / qualifiedName
- `assetKey`: platform 统一资产键
- `created`: 是否本次自动创建
- `resolvedBy`: `schema_table` 或 `openlineage_discovery`

## 自动创建资产规则

- 生命周期：`PENDING_GOVERNANCE`
- `description` 写入 namespace、rawName、role、job、runId
- `snapshotTime` 写入当前时间
- 不默认标记为 `ACTIVE`

## 血缘证明

`catalog_dataset_lineage.notes` 补充：

- OpenLineage namespace/job
- upstream assetKey
- downstream assetKey
- runId

## 后续依赖

- F3/T05 的血缘失败和阻断报告会消费该资产证据。
- Sprint-31 发布门禁可以把 `created=true` 且未治理资产识别为治理阻断或 warning。

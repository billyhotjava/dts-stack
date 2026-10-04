# Sprint-31A F2/T03 Schema/Column 读取契约

## 目标

为 SQL IDE、`dts-metrics` 和后续语义建模提供统一字段读取接口。消费方只通过 platform 契约读取字段候选，不直接读取 OM column cache 或本地 `catalog_column_schema`。

## 当前落地

新增端点：

```text
GET /api/catalog/assets-v2/{id}/schema-contract
```

该端点先读取 OpenMetadata 字段缓存；如果 OM 字段为空且资产已映射到 DTS 原生 `CatalogDataset`，则 fallback 到本地 Catalog schema/column。

## 返回契约

| 字段 | 说明 |
|------|------|
| `asset` | F2/T02 的资产读取契约 |
| `columns` | 字段契约列表 |
| `columnCount` | 字段数量 |
| `schemaSource` | 字段事实来源：`openmetadata-cache` 或 `dts-catalog` |

字段契约：

| 字段 | 说明 |
|------|------|
| `name` | 字段名 |
| `dataType` | 字段类型 |
| `nullable` | 是否可空；OM 暂无该字段时返回 `null` |
| `ordinalPosition` | 字段顺序；本地 Catalog 没有物理顺序时按字段名稳定排序并生成序号 |
| `description` | 字段说明或注释 |
| `tags` | 普通标签 |
| `sensitiveTags` | 敏感标签 |
| `standardId` | 已绑定的数据元标准 ID |
| `standardRule` | 标准映射规则 |
| `status` | 字段状态 |
| `source` | 字段来源 |
| `updatedAt` | 字段更新时间 |

## 权限原则

- 读取字段前先按资产契约检查资产可见性。
- 无权读取资产时返回 `404`，不允许字段枚举。
- 字段读取契约不负责授予权限；权限校验在 F4 收敛到 platform `asset_grant`。

## 后续依赖

- `dts-metrics` 使用该契约生成业务对象字段候选、指标维度候选和公式字段选择器。
- F3 使用该契约补字段级血缘和来源证明。
- F5 在资产详情页展示字段治理缺口和标准绑定状态。

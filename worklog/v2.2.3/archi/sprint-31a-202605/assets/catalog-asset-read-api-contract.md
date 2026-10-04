# Sprint-31A F2/T02 Catalog 资产读取 API 契约

## 目标

`dts-platform` 对外提供稳定的只读资产契约，供 `dts-metrics`、`dts-analytics`、SQL IDE 和 platform-webapp 消费。消费方不直接读取 `catalog_dataset`、`om_asset_cache`、`catalog_asset_extension` 或映射表。

## 当前落地

新增端点：

```text
GET /api/catalog/assets-v2/{id}/contract
```

该端点支持两类 ID：

- OpenMetadata cache 资产 ID。
- DTS 原生 `CatalogDataset` ID。

## 返回契约

| 字段 | 说明 |
|------|------|
| `assetType` | 统一资产类型，当前数据表统一为 `DATASET` |
| `assetKey` | 稳定资产键，用于跨 OpenMetadata、本地 Catalog、dbt、BI Dataset 做映射 |
| `grantAssetType` | platform `asset_grant` 使用的资产类型 |
| `grantAssetId` | platform `asset_grant` 使用的资产 ID，优先使用 legacy dataset ID，其次使用 OM cache ID |
| `sourceRef` | 来源系统稳定引用，例如 `dts-catalog:{id}` 或 `openmetadata:{entityId}` |
| `classification` | 数据密级 |
| `warehouseLayer` | 数仓层级 |
| `domainId` | platform 治理主题域 ID |
| `lifecycleStatus` | 资产生命周期状态 |
| `governanceStatus` | 治理状态 |
| `missingGovernanceFields` | 阻断消费的治理缺口 |
| `consumable` | 是否满足启用、ACTIVE 生命周期和治理字段完整性 |
| `metadataSource` | 资产事实来源，当前为 `openmetadata-cache` 或 `dts-catalog` |

## 权限原则

- 契约端点复用资产详情读取的可见性规则。
- 无权访问时返回 `404`，避免暴露资产存在性。
- 本任务只补稳定读取契约；列表/详情权限完全等价在 F4/T02 收口。

## 后续依赖

- F2/T03 在该契约基础上补 schema/column 读取契约。
- F2/T05 输出 capabilities，让 `dts-metrics` 启动时判断平台契约是否可用。
- F4/T01-F4/T02 将 `grantAssetType/grantAssetId` 接入统一权限校验。

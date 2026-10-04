# T02: Catalog 资产读取 API 契约

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

为 platform-webapp、dts-metrics、dts-analytics 和 SQL IDE 提供统一资产列表与详情 API。

## 技术设计

- 新增 `GET /api/catalog/assets-v2/{id}/contract`，输出资产身份、授权键、来源、生命周期、治理字段和可消费状态。
- 契约端点同时支持 OpenMetadata cache 资产 ID 和 DTS 原生 `CatalogDataset` ID。
- 读取时复用详情可见性规则，无权访问返回 `404`。
- 列表和详情完全等价的权限过滤继续在 F4/T02 收口。

## 影响范围

- CatalogAssetPortalService
- Catalog REST resource
- platform webapp asset API
- `worklog/v2.2.3/sprint-31a-202605/assets/catalog-asset-read-api-contract.md`

## 验证

- [x] 契约端点不直接暴露内部 Catalog / OM cache 表结构。
- [x] 无权读取资产时返回 `404`。
- [ ] 列表不暴露详情页不可访问资产。（F4/T02）
- [ ] 筛选条件组合不会退化为全量泄露。（F4/T02）

## 完成标准

- [x] 消费方可通过资产契约获取 `assetType / assetKey / grantAssetId / governanceStatus / missingGovernanceFields`。
- [x] 契约文档已写入 `assets/catalog-asset-read-api-contract.md`。

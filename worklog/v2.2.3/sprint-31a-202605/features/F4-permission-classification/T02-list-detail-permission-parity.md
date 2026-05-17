# T02: 列表和详情权限一致性

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

修复资产列表可见但详情被拒、或列表泄露无权资产摘要的问题。

## 技术设计

- Catalog 列表摘要生成前复用详情侧 `canRead(extension, legacy, activeDept)`。
- OpenMetadata cache 和 DTS legacy catalog 均按相同入口过滤。
- 对不可见资产不返回摘要，也不计入列表返回 `total`，避免通过分页总数泄露资产存在性。

## 影响范围

- CatalogAssetPortalService
- Dataset detail APIs
- frontend asset list/detail

## 验证

- [x] 无权用户列表看不到 OpenMetadata 资产摘要。
- [x] 无权用户列表看不到 DTS legacy 资产摘要。
- [x] 直接访问详情仍复用原有详情权限判断。

## 完成标准

- [x] 列表与详情访问结果一致。

## 交付物

- `CatalogAssetPortalService.listAssets` 权限过滤修复
- `CatalogAssetPortalService.listLegacyAssets` 权限过滤修复
- `CatalogAssetPortalServicePermissionParityTest`
- `worklog/v2.2.3/sprint-31a-202605/assets/catalog-list-detail-permission-parity.md`

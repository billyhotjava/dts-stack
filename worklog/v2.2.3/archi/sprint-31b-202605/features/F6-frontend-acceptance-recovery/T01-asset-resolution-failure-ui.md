# T01: 资产解析失败报告前端闭环

**优先级**: P0
**状态**: DONE
**依赖**: F1/T05

## 目标

把 Sprint-31B F1/T05 新增的 `catalog_asset_resolution_failure` 从内部审计表和内部接口，补齐到数据资产中心可见的前端闭环。

## 技术设计

1. 在资产门户公开只读端点：
   - `GET /api/catalog/assets-v2/resolution-failures`
   - 受 `CATALOG_MAINTAINERS` 控制；
   - 返回 `ref、requestedAt、caller、typeHintGuess、reason`。
2. 在 `dts-platform-webapp` 资产地图增加“解析失败”按钮。
3. 弹窗展示最近 100 条失败记录，支持定位：
   - 失败引用；
   - 类型猜测；
   - 失败原因；
   - 调用方；
   - 发生时间。
4. 将端点加入 platform capability `catalog.readEndpoints`，避免跨服务契约遗漏。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogAssetPortalResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/capability/PlatformCapabilityResource.java`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- `source/dts-platform-webapp/src/pages/catalog/DatasetsPage.tsx`

## 验证

- [x] `./mvnw -q -pl dts-platform -Dtest=CatalogAssetPortalResourceTest,CatalogAssetResolutionFailureResourceTest test`
- [x] `./mvnw -q -pl dts-platform -Dtest=PlatformCapabilityResourceTest,CatalogAssetPortalResourceTest,CatalogAssetResolutionFailureResourceTest test`
- [x] `pnpm build` from `source/dts-platform-webapp`

## 完成标准

- [x] 数据资产中心页面能打开解析失败列表。
- [x] 后端只读端点有测试覆盖。
- [x] capability contract 暴露该端点。

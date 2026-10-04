# T01: visual asset adapter 与 platform contract client

**优先级**: P0
**状态**: DONE
**依赖**: F2

## 目标

让 `dts-metrics` 通过 platform internal contract 获取可建模资产，自己不保存资产事实源，也不直读 platform 表。

## 技术设计

- 当前 `PlatformContractClient` 已增加 `listCatalogAssets(...)` 和 `getCatalogAssetSchemaContract(...)`。
- 当前 `MetricVisualAssetResource` 将 platform 返回的 Catalog 资产转换为 metrics `VisualAssetSummary`。
- 当前默认过滤 DWS/ADS；DWD 仅在 `includeDrilldown=true` 高级模式通过。
- 当前 platform 不可用时返回 `503 platform catalog contract unavailable`，不 fallback 静态资产。

## 影响范围

- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/PlatformContractClient.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/web/rest/**`
- `source/dts-platform` internal resource

## 验证

- [x] Client 测试覆盖 URL 拼接，防止 `/api/api/internal` 双前缀。
- [x] Platform 503 时 metrics 不 fallback 静态资产。
- [x] DWS/ADS/DWD 过滤符合 F1 准入规则。

## 完成标准

- [x] 资产入口由 platform contract 驱动。

# T02: assets-v2 透出列族

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
`/catalog/assets-v2`（列表 + schema-contract）透出列族，命名与 dts-metrics `VisualAssetSummary` 对齐，供 F4 消费。

## 技术设计
- `CatalogAssetPortalResource` / Service：资产项补 `dimensionColumns/metricColumns/timeColumns/grain/standardCodes`（复用 T01 派生器）。
- 列表视图按需透出（避免 N+1：列族可能需逐资产列同步——评估批量）。
- 命名对齐 dts-metrics 前端契约（camelCase）。

## 影响范围
- `dts-platform`：`CatalogAssetPortalResource` / `CatalogAssetPortalService`。

## 验证
- [ ] assets-v2 资产项含列族（非空，对有 meta 的资产）。
- [ ] 字段名与 `VisualAssetSummary` 一致。

## 完成标准
- [ ] 列族透出、命名对齐、性能可接受（无 N+1 回归）、`clean test` 通过。

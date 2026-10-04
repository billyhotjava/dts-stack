# T01: 资产地图按层级和状态收敛

**优先级**: P1
**状态**: DONE
**依赖**: F2

## 目标

让资产地图围绕 ODS/STG/DWD/DWS/ADS 和治理状态展示，避免过多并列卡片造成认知负担。

## 技术设计

- 按 warehouseLayer、lifecycleStatus、governance gap 分组。
- 保留 KPI，但减少重复侧边统计。
- 支持快速定位 `PENDING_GOVERNANCE`。
- 接入治理缺口和血缘缺口报告，列表卡片展示“可引用 / 治理阻断 / 待确认 / 主目录缓存”。

## 影响范围

- DatasetsPage / AssetMap
- Catalog summary API

## 验证

- [x] 大量资产时页面仍可扫描。
- [x] 缺口资产可直接进入详情。

## 完成标准

- [x] 资产地图成为治理入口。

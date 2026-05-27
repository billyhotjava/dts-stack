# T01: 可视化资产查询 API

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标

提供前端可用的可视化资产查询 API，默认只返回已治理、已授权的 DWS/ADS 资产。

## 技术设计

- 新增或固化 `GET /api/metrics/visual-assets`。
- Query 支持 `layers`、`domainCode`、`businessObjectCode`、`keyword`、`page`、`size`、`mode=advanced`。
- 默认 `layers=DWS,ADS`；`layers=DWD` 必须配合 `mode=advanced`。
- `dts-metrics` 调 platform `GET /api/internal/metrics/visual-assets` 获取真实资产、权限、治理和 lineage 状态。

## 影响范围

- `source/dts-metrics/src/main/java/**/web/rest`
- `source/dts-metrics/src/main/java/**/service/PlatformContractClient.java`
- `source/dts-metrics-webapp/src/api.ts`
- `source/dts-metrics-webapp/src/types.ts`

## 验证

- [ ] 默认查询不返回 DWD/ODS/STG。
- [ ] 无权限资产返回过滤结果或 `asset_permission_denied`，不泄露敏感名称。
- [ ] 返回 DTO 包含 `warehouseLayer`、`grain`、`permissionDecision`。

## 完成标准

- [ ] 前端资产导航不再依赖静态数组。

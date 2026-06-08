# T02: Graph draft 与 preflight API

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

定义 graph draft 的保存、读取和本地预检 API，确保节点携带数据层、资产和校验状态。

## 技术设计

- 当前已新增 `POST /api/metrics/graphs/draft/preflight`，用于替换前端本地 SQL/RLS placeholder 预览。
- 当前已新增 `POST /api/metrics/graphs` 创建草稿。
- 当前已新增 `GET /api/metrics/graphs/{graphId}` 读取草稿。
- 当前已新增 `PATCH /api/metrics/graphs/{graphId}` 保存节点、边、布局和配置。
- 当前已新增 `POST /api/metrics/graphs/{graphId}/preflight` 检查已保存 graph 的结构、层级、Join、指标依赖、字段引用、标准码和公式 DSL。
- 当前 `GraphNode` 已携带 `assetKey`、`warehouseLayer`、grain/primary key 等分层诊断字段；`validationState` 在响应 status/diagnostics 中表达。

## 影响范围

- `source/dts-metrics` graph domain / persistence / preflight service
- `source/dts-metrics-webapp` React Flow state adapter

## 验证

- [x] ODS/STG 节点进入 preflight 时返回 `invalid_layer`。
- [x] DWD 节点缺 grain/primary key 时返回 `grain_mismatch`。
- [x] 诊断包含 `nodeId` 或 `edgeId`。

## 完成标准

- [x] graph draft 可重放，且分层信息不会在保存/加载中丢失。

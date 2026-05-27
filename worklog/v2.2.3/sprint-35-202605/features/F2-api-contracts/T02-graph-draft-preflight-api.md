# T02: Graph draft 与 preflight API

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

定义 graph draft 的保存、读取和本地预检 API，确保节点携带数据层、资产和校验状态。

## 技术设计

- `POST /api/metrics/graphs` 创建草稿。
- `GET /api/metrics/graphs/{graphId}` 读取草稿。
- `PATCH /api/metrics/graphs/{graphId}` 保存节点、边、布局和配置。
- `POST /api/metrics/graphs/{graphId}/preflight` 检查 graph 结构、层级、Join、指标依赖、字段引用、标准码和公式 DSL。
- `GraphNode` 必须包含 `sourceAssetKey`、`warehouseLayer`、`validationState`。

## 影响范围

- `source/dts-metrics` graph domain / persistence / preflight service
- `source/dts-metrics-webapp` React Flow state adapter

## 验证

- [ ] ODS/STG 节点进入 preflight 时返回 `invalid_layer`。
- [ ] DWD 节点缺 grain/primary key 时返回 `grain_mismatch`。
- [ ] 诊断包含 `nodeId` 或 `edgeId`。

## 完成标准

- [ ] graph draft 可重放，且分层信息不会在保存/加载中丢失。

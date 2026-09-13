# T01: 核心契约 DTO record 化

**优先级**: P1
**状态**: READY
**依赖**: F1

## 目标

为 dts-metrics 核心契约对象建立 record DTO，字段对齐 `worklog/v2.2.3/sprint-35-202605/assets/dts-metrics-api-contract.md`。

## 技术设计

- 新增 record：`VisualAssetSummary`（assetKey/warehouseLayer/domainCode/grain/primaryKeys/timeColumns/metricColumns/dimensionColumns/governanceStatus/lineageStatus/permissionDecision）、`GraphNode`（sourceAssetKey/warehouseLayer/validationState）、`ValidationDiagnostic`（nodeId|edgeId/fieldId/severity/code/message）、`ModelState`（modelId/modelName/status/artifactRef/policySource/predicateHash/activeVersion）。
- 与前端 `semanticTypes.ts` 的 TS 类型保持字段一致（契约同源）。

## 影响范围
- 新增 `service/dto/` record；Resource/Service 边界逐步采用。

## 验证
- [ ] DTO 字段与 API 契约文档逐项核对一致。

## 完成标准
- [ ] 核心契约对象有强类型定义，供 T02 渐进替换 Map 使用。

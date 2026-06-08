# T04: DTO、错误码和 TypeScript contract

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01-T03

## 目标

固化前后端共享的 DTO 和错误码，避免 UI 和后端各自解释层级、状态和诊断。

## 技术设计

- 定义 `WarehouseLayer = DWD | DWS | ADS`，ODS/STG 只在 lineage DTO 中出现。
- 定义 `VisualAssetSummary`、`GraphDraft`、`GraphNode`、`GraphEdge`、`ValidationDiagnostic`、`PublishReference`。
- 错误码覆盖 `invalid_layer`、`grain_mismatch`、`asset_permission_denied`、`graph_validation_failed`、`dbt_validation_failed`、`platform_contract_unavailable`。
- TypeScript contract 放入 `source/dts-metrics-webapp/src/types.ts` 或独立 `features/metrics/contracts.ts`。
- 已新增 Java `MetricLifecycleStatus` / `MetricContractErrorCode` 与前端 `MetricLifecycleStatus` / `MetricContractErrorCode` / `ValidationDiagnostic` / `PublishReference` / `ModelVersionHistory` 类型。

## 影响范围

- `source/dts-metrics-webapp/src/types.ts`
- `source/dts-metrics` DTO records
- `worklog/v2.2.3/sprint-35-202605/assets/dts-metrics-api-contract.md`

## 验证

- [x] 前端 source contract test 校验 Sprint-35 feature routes、visual assets、graph draft 和 model lifecycle API path。
- [x] 后端 focused tests 覆盖 `graph_validation_failed`、`dbt_validation_required`、`platform_contract_unavailable` 的关键路径。
- [x] publish 响应测试确认不返回候选 artifact、platform 原始 release payload、dbt 文件路径或 secret。
- [x] lifecycle 状态、核心错误码和版本/回滚响应已收口到独立 Java enum 与 TS union/type。
- [ ] `VisualAssetSummary`、`GraphDraft` 等后端 Java DTO records 仍待从 Map 返回中收口。

## 完成标准

- [ ] 前后端以同一字段解释分层、状态和诊断；当前已覆盖 source contract、关键错误码、lifecycle 状态和前端 TS 类型，完整后端 DTO records 待后续补齐。

# T04: DTO、错误码和 TypeScript contract

**优先级**: P0
**状态**: READY
**依赖**: T01-T03

## 目标

固化前后端共享的 DTO 和错误码，避免 UI 和后端各自解释层级、状态和诊断。

## 技术设计

- 定义 `WarehouseLayer = DWD | DWS | ADS`，ODS/STG 只在 lineage DTO 中出现。
- 定义 `VisualAssetSummary`、`GraphDraft`、`GraphNode`、`GraphEdge`、`ValidationDiagnostic`、`PublishReference`。
- 错误码覆盖 `invalid_layer`、`grain_mismatch`、`asset_permission_denied`、`graph_validation_failed`、`dbt_validation_failed`、`platform_contract_unavailable`。
- TypeScript contract 放入 `source/dts-metrics-webapp/src/types.ts` 或独立 `features/metrics/contracts.ts`。

## 影响范围

- `source/dts-metrics-webapp/src/types.ts`
- `source/dts-metrics` DTO records
- `worklog/v2.2.3/sprint-35-202605/assets/dts-metrics-api-contract.md`

## 验证

- [ ] 前端 source contract test 校验 API path 和关键字段。
- [ ] 后端 JSON 测试校验错误码稳定。
- [ ] DTO 中没有平台内部表名、dbt 文件路径或数据源密码字段。

## 完成标准

- [ ] 前后端以同一字段解释分层、状态和诊断。
